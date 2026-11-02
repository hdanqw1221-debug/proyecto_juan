"""Prototipado rápido de la forma de un órgano sin Blender: campo -> surface nets -> Taubin -> OBJ -> hoja.

Uso (desde la carpeta cavidades/):
    python herramientas/prototipo_organo.py ID [ID ...] [--salida DIR] [--h PASO] [--contexto ID,ID]
                                            [--sin-hoja] [--sin-diezmar] [--antes]

Reproduce el flujo del generador de Blender (anatomia/organos + recorte a la cavidad), salvo el
remallado por vóxeles; el diezmado final se imita con VTK. El contexto (líneas medias relajadas
de los tubos digestivos y esófago prolongado) se lee de modelos/contexto_organos.json.

Salida en DIR (por defecto prototipos/<ID>/):
    obj/<ID>.obj, manifiesto.json      malla nueva (+ el resto de estructuras actuales como contexto)
    hoja.png                           8 vistas, con las estructuras de --contexto translúcidas
    antes.png                          (--antes) la misma hoja con la malla actual, para comparar
    metricas.json                      triángulos, volumen, piezas sueltas, agujeros (género),
                                       manifold y penetración en las estructuras vecinas actuales
"""
import argparse
import json
import sys
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RAIZ))

import numpy as np  # noqa: E402

from anatomia import catalogo as cat  # noqa: E402
from anatomia import referencia as ref  # noqa: E402
from anatomia.organos import contexto as Ctx  # noqa: E402
from anatomia.organos import diafragma  # noqa: E402
from blender import primitivas as P  # noqa: E402
from blender.exportar import escribir_obj, leer_contexto, leer_obj  # noqa: E402

DIR_MODELOS = RAIZ / "modelos"
TRIS_MAX = 15000
REMALLADO = {}  # objeto_id -> tamaño de vóxel con el que Blender remallará la pieza (m)


# ----------------------------------------------------------------------------
# Mallado
# ----------------------------------------------------------------------------
def triangular(caras):
    T = []
    for f in caras:
        for k in range(1, len(f) - 1):
            T.append((f[0], f[k], f[k + 1]))
    return np.array(T, dtype=np.int64)


def compactar(V, T):
    usados = np.unique(T)
    mapa = -np.ones(len(V), dtype=np.int64)
    mapa[usados] = np.arange(len(usados))
    return np.asarray(V)[usados], mapa[T]


def aristas(T):
    e = np.sort(np.vstack([T[:, [0, 1]], T[:, [1, 2]], T[:, [2, 0]]]), axis=1)
    return np.unique(e, axis=0)


def mallar(oid, ctx, h=None):
    """Malla (V, T) de la pieza con el mismo flujo que el generador (sin remallado por vóxeles)."""
    if oid == "TOR_DIAFRAGMA":  # lámina explícita: se muestran las piezas sin aplicar los booleanos
        V, F = P.unir_mallas([diafragma.malla()] + list(diafragma.uniones()))
        return compactar(V, triangular(F))
    mod = Ctx.modulo(oid)
    if mod is None:
        raise SystemExit(f"{oid} no es un órgano macizo de anatomia/organos (los tubos no se prototipan aquí)")
    pz = ctx.pieza(oid)
    mn, mx = pz["caja"]
    h = h or pz["h"]
    funcion = lambda p: mod.campo(oid, p, ctx)  # noqa: E731
    V, F = P.superficie_por_campo(funcion, mn, mx, h, sigma=pz.get("sigma", 1.0))
    if len(F) == 0:
        raise SystemExit(f"{oid}: el campo no produjo superficie dentro de la caja {pz['caja']}")
    # Blender remalla por vóxeles de tamaño v (materializar en generar_cavidades.py): si v es mayor que el
    # paso de la rejilla, el detalle fino se pierde. Se imita extrayendo de nuevo la superficie a ese paso.
    radio_min = pz.get("radio_min") or 3 * h
    v = pz.get("voxel") or min(np.sqrt(2.0 * P.area_malla(V, F) / 9000), radio_min / 2.2)
    v = max(v, 0.0012)
    REMALLADO[oid] = round(float(v), 4)
    if v > 1.15 * h:
        V, F = P.superficie_por_campo(funcion, mn, mx, v, sigma=pz.get("sigma", 1.0))
    V, T = compactar(V, triangular(F))
    A = aristas(T)
    if pz.get("taubin", 4):
        V = P.suavizar_laplaciano(V, A, iteraciones=pz.get("taubin", 4), lam=0.5, mu=-0.53)
    recorte = pz.get("recorte")
    if recorte:
        V2 = Ctx.recortar(V, recorte)
        if np.any(np.linalg.norm(V2 - V, axis=1) > 1e-6):
            V2 = P.suavizar_laplaciano(V2, A, iteraciones=2, lam=0.5)
            V2 = Ctx.recortar(V2, recorte)
        V = V2
    return V, T


def a_polydata(V, T):
    import vtk
    from vtk.util.numpy_support import numpy_to_vtk, numpy_to_vtkIdTypeArray

    pd = vtk.vtkPolyData()
    pts = vtk.vtkPoints()
    pts.SetData(numpy_to_vtk(np.ascontiguousarray(V, dtype=float), deep=True))
    pd.SetPoints(pts)
    desplazamientos = numpy_to_vtkIdTypeArray(np.arange(0, 3 * len(T) + 1, 3, dtype=np.int64), deep=True)
    conectividad = numpy_to_vtkIdTypeArray(np.ascontiguousarray(T, dtype=np.int64).ravel(), deep=True)
    ca = vtk.vtkCellArray()
    ca.SetData(desplazamientos, conectividad)
    pd.SetPolys(ca)
    return pd


def de_polydata(pd):
    from vtk.util.numpy_support import vtk_to_numpy

    V = vtk_to_numpy(pd.GetPoints().GetData()).astype(float)
    T = vtk_to_numpy(pd.GetPolys().GetConnectivityArray()).reshape(-1, 3).astype(np.int64)
    return V, T


def diezmar(V, T, tris_max=TRIS_MAX):
    if len(T) <= tris_max:
        return V, T
    import vtk

    dec = vtk.vtkQuadricDecimation()
    dec.SetInputData(a_polydata(V, T))
    dec.SetTargetReduction(1.0 - (tris_max * 0.97) / len(T))
    dec.VolumePreservationOn()
    dec.Update()
    return de_polydata(dec.GetOutput())


# ----------------------------------------------------------------------------
# Métricas
# ----------------------------------------------------------------------------
def componentes(V, T):
    """Etiqueta de componente conexa de cada triángulo (unión-búsqueda sobre vértices)."""
    padre = np.arange(len(V))

    def raiz(i):
        while padre[i] != i:
            padre[i] = padre[padre[i]]
            i = padre[i]
        return i

    for a, b, c in T:
        ra, rb, rc = raiz(a), raiz(b), raiz(c)
        padre[rb] = ra
        padre[raiz(rc)] = ra
    raices = np.array([raiz(i) for i in range(len(V))])
    return raices[T[:, 0]]


def metricas(V, T, oid, ctx, vecinos):
    e_todas = np.sort(np.vstack([T[:, [0, 1]], T[:, [1, 2]], T[:, [2, 0]]]), axis=1)
    _, cuenta = np.unique(e_todas, axis=0, return_counts=True)
    comp = componentes(V, T)
    piezas = []
    for c in np.unique(comp):
        Tc = T[comp == c]
        Vc = np.unique(Tc)
        Ec = len(np.unique(np.sort(np.vstack([Tc[:, [0, 1]], Tc[:, [1, 2]], Tc[:, [2, 0]]]), axis=1), axis=0))
        chi = len(Vc) - Ec + len(Tc)
        vol, _ = P.volumen_y_centroide(V, Tc)
        piezas.append({"triangulos": int(len(Tc)), "genero": int(round((2 - chi) / 2)), "volumen_cm3": round(abs(vol) * 1e6, 1)})
    piezas.sort(key=lambda d: -d["triangulos"])
    vol, cen = P.volumen_y_centroide(V, T)
    meta = cat.POR_ID[oid]
    cav_centroide = ref.cavidad_de_punto(*cen)
    m = {
        "objeto_id": oid,
        "triangulos": int(len(T)),
        # Surface nets deja algunas aristas no manifold en celdas ambiguas; el remallado por vóxeles de
        # Blender las elimina. Pocas decenas son normales; cientos indican láminas o puentes muy finos.
        "aristas_no_manifold": int(np.sum(cuenta != 2)),
        "piezas_sueltas": len(piezas) - 1,
        "genero_pieza_principal": piezas[0]["genero"],
        "piezas": piezas[:6],
        "volumen_cm3": round(abs(vol) * 1e6, 1),
        "centroide": [round(float(c), 4) for c in cen],
        "caja": [[round(float(c), 4) for c in V.min(0)], [round(float(c), 4) for c in V.max(0)]],
        "dimensiones_cm": [round(float(d) * 100, 1) for d in np.ptp(V, axis=0)],
        "cavidad_del_centroide": cav_centroide,
        "cavidad_esperada": meta["cavidad"],
        # Resolución real de la malla final: Blender remalla por vóxeles de este tamaño antes de diezmar.
        # Para conservar bordes finos o surcos, fije "voxel" en PIEZAS (p. ej., 0.004).
        "voxel_remallado_m": REMALLADO.get(oid),
        "penetracion_en_vecinos": vecinos,
    }
    return m


def penetracion(V, T, por_id, excluir, umbral=0.003):
    """Profundidad máxima (m) de la malla dentro de las estructuras actuales cuya caja se solapa con ella."""
    import vtk

    pd = a_polydata(V, T)
    mn, mx = V.min(0) - 0.01, V.max(0) + 0.01
    salida = {}
    for oid, o in por_id.items():
        if oid in excluir or o.get("envoltura") or o["cavidad"] == "referencia":
            continue
        (bmn, bmx) = (np.array(o["caja"][0]), np.array(o["caja"][1]))
        if np.any(bmn > mx) or np.any(mn > bmx):
            continue
        Vb, Tb = leer_obj(o["archivo"])
        superficie = a_polydata(Vb, Tb)
        muestra = V[:: max(1, len(V) // 4000)]
        # Dentro / fuera por rayos (robusto en mallas cerradas); profundidad = distancia a la superficie.
        puntos = vtk.vtkPolyData()
        pts = vtk.vtkPoints()
        for q in muestra:
            pts.InsertNextPoint(*q)
        puntos.SetPoints(pts)
        sel = vtk.vtkSelectEnclosedPoints()
        sel.SetInputData(puntos)
        sel.SetSurfaceData(superficie)
        sel.SetTolerance(1e-6)
        sel.Update()
        dentro = np.array([sel.IsInside(i) for i in range(len(muestra))], dtype=bool)
        if not dentro.any():
            continue
        loc = vtk.vtkCellLocator()
        loc.SetDataSet(superficie)
        loc.BuildLocator()
        prof = np.zeros(len(muestra))
        cp, cid, sid, d2 = [0.0, 0.0, 0.0], vtk.reference(0), vtk.reference(0), vtk.reference(0.0)
        for i in np.nonzero(dentro)[0]:
            loc.FindClosestPoint(list(muestra[i]), cp, cid, sid, d2)
            prof[i] = float(d2) ** 0.5
        if prof.max() > umbral:
            salida[oid] = {"profundidad_max_cm": round(float(prof.max()) * 100, 1),
                           "vertices_dentro_pct": round(100.0 * float(np.mean(prof > umbral)), 1)}
    return dict(sorted(salida.items(), key=lambda kv: -kv[1]["profundidad_max_cm"]))


# ----------------------------------------------------------------------------
# Programa
# ----------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("ids", nargs="+")
    ap.add_argument("--salida", default=None)
    ap.add_argument("--h", type=float, default=None, help="paso de rejilla (por defecto el de la pieza)")
    ap.add_argument("--contexto", default="", help="estructuras actuales a dibujar translúcidas en la hoja")
    ap.add_argument("--sin-hoja", action="store_true")
    ap.add_argument("--sin-diezmar", action="store_true")
    ap.add_argument("--antes", action="store_true", help="hoja de la malla actual para comparar")
    ap.add_argument("--reemplazos", default="",
                    help="carpetas de otros prototipos (separadas por comas) cuyas mallas sustituyen a las actuales "
                         "como vecinas (penetración y hoja), p. ej. prototipos/corazon")
    a = ap.parse_args()

    manifiesto = json.loads((DIR_MODELOS / "manifiesto.json").read_text(encoding="utf-8"))
    for o in manifiesto["objetos"]:
        o["archivo"] = str((DIR_MODELOS / o["archivo"]).resolve())
    por_id = {o["objeto_id"]: o for o in manifiesto["objetos"]}
    for carpeta in [c for c in a.reemplazos.split(",") if c]:
        carpeta = Path(carpeta) if Path(carpeta).is_absolute() else (Path.cwd() / carpeta)
        for m in json.loads((carpeta / "metricas.json").read_text(encoding="utf-8")):
            por_id[m["objeto_id"]].update({"archivo": str((carpeta / "obj" / f"{m['objeto_id']}.obj").resolve()),
                                           "caja": m["caja"], "centroide": m["centroide"]})
    salida = Path(a.salida or (RAIZ / "prototipos" / "_".join(a.ids)))
    (salida / "obj").mkdir(parents=True, exist_ok=True)
    ctx = leer_contexto(DIR_MODELOS)
    nuevos = {}
    todas_metricas = []
    for oid in a.ids:
        t0 = time.time()
        V, T = mallar(oid, ctx, a.h)
        if not a.sin_diezmar:
            V, T = diezmar(V, T)
        escribir_obj(salida / "obj" / f"{oid}.obj", oid, V, T)
        excluir = {oid} | {b for par in cat.UNIONES if oid in par for b in par} | set(a.ids)
        vec = penetracion(V, T, por_id, excluir)
        m = metricas(V, T, oid, ctx, vec)
        m["segundos"] = round(time.time() - t0, 1)
        todas_metricas.append(m)
        nuevos[oid] = m
        print(json.dumps(m, ensure_ascii=False, indent=1))
    # Manifiesto de la carpeta: las piezas nuevas y, como contexto, las actuales (rutas absolutas).
    objetos = []
    for o in manifiesto["objetos"]:
        o = dict(por_id[o["objeto_id"]])
        if o["objeto_id"] in nuevos:
            m = nuevos[o["objeto_id"]]
            o.update({"archivo": f"obj/{o['objeto_id']}.obj", "n_triangulos": m["triangulos"],
                      "centroide": m["centroide"], "caja": m["caja"]})
        objetos.append(o)
    (salida / "manifiesto.json").write_text(json.dumps({**manifiesto, "objetos": objetos}, ensure_ascii=False),
                                            encoding="utf-8")
    (salida / "metricas.json").write_text(json.dumps(todas_metricas, ensure_ascii=False, indent=1), encoding="utf-8")
    if not a.sin_hoja:
        from visor.hoja_organo import hoja

        contexto = [c for c in a.contexto.split(",") if c]
        print(hoja(a.ids, salida, salida / "hoja.png", contexto))
        if a.antes:
            print(hoja(a.ids, DIR_MODELOS, salida / "antes.png", contexto))


if __name__ == "__main__":
    main()
