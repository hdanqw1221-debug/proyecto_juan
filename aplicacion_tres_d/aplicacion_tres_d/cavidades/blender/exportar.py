"""Exportación: un OBJ por objeto, escena GLB, archivo .blend, manifiesto e informes.

Si existe `modelos/refinados/<objeto_id>.obj` (mismo sistema de coordenadas),
se usa en lugar de la malla procedural y se informa la desviación del
centroide y del volumen (§10 del prompt).
"""
import json
from pathlib import Path

import numpy as np

from anatomia import catalogo as cat
from anatomia import referencia as ref
from blender import primitivas as P

CAMPOS_MANIFIESTO = ("objeto_id", "ficha_id", "nombre", "cavidad", "sistema", "lado", "color_hex", "opacidad",
                     "capa", "seleccionable", "visible_por_defecto", "coleccion", "sexo_aplicable", "envoltura")


def escribir_obj(ruta, nombre, V, T):
    lineas = [f"# {nombre} - generado por cavidades/blender/generar_cavidades.py", f"o {nombre}"]
    lineas += [f"v {x:.5f} {y:.5f} {z:.5f}" for x, y, z in V]
    lineas += [f"f {a + 1} {b + 1} {c + 1}" for a, b, c in T]
    Path(ruta).write_text("\n".join(lineas) + "\n", encoding="utf-8")


def leer_obj(ruta):
    """Lector OBJ mínimo (v/f; caras poligonales triangulizadas en abanico)."""
    V, T = [], []
    for linea in Path(ruta).read_text(encoding="utf-8", errors="ignore").splitlines():
        partes = linea.split()
        if not partes:
            continue
        if partes[0] == "v":
            V.append([float(c) for c in partes[1:4]])
        elif partes[0] == "f":
            idx = [int(c.split("/")[0]) for c in partes[1:]]
            idx = [i - 1 if i > 0 else len(V) + i for i in idx]
            T += [(idx[0], idx[k], idx[k + 1]) for k in range(1, len(idx) - 1)]
    return np.array(V, dtype=float), np.array(T, dtype=np.int64)


def _datos_mundo(obj):
    from blender.generar_cavidades import malla_a_numpy

    V, T = malla_a_numpy(obj.data)
    mw = np.array(obj.matrix_world)
    return V @ mw[:3, :3].T + mw[:3, 3], T


def exportar_todo(salida, mallas, colisiones, desplazamientos, ajustes, con_glb=True):
    import bpy

    from blender.generar_cavidades import log, reemplazar_malla

    salida = Path(salida)
    (salida / "obj").mkdir(parents=True, exist_ok=True)
    dir_ref = salida / "refinados"
    objetos, refinados = [], []
    for meta in cat.OBJETOS:
        oid = meta["objeto_id"]
        if oid not in mallas:
            continue
        obj = mallas[oid]
        V, T = _datos_mundo(obj)
        vol, cen = P.volumen_y_centroide(V, T)
        es_refinado = (dir_ref / f"{oid}.obj").exists()
        if es_refinado:
            Vr, Tr = leer_obj(dir_ref / f"{oid}.obj")
            vol_r, cen_r = P.volumen_y_centroide(Vr, Tr)
            refinados.append({"objeto_id": oid,
                              "desviacion_centroide_m": round(float(np.linalg.norm(cen_r - cen)), 4),
                              "variacion_volumen_pct": round(100.0 * (vol_r - vol) / vol, 1) if vol else None})
            reemplazar_malla(obj, Vr, Tr)
            V, T, vol, cen = Vr, Tr, vol_r, cen_r
            log(f"{oid}: se usa la malla refinada")
        archivo = f"obj/{oid}.obj"
        escribir_obj(salida / archivo, oid, V, T)
        entrada = {k: meta[k] for k in CAMPOS_MANIFIESTO}
        entrada.update({
            "archivo": archivo,
            "n_triangulos": int(len(T)),
            "manifold": bool(obj.get("manifold", False)),
            "volumen_m3": round(abs(vol), 6),
            "centroide": [round(float(c), 4) for c in cen],
            "caja": [[round(float(c), 4) for c in V.min(0)], [round(float(c), 4) for c in V.max(0)]],
            "refinado": es_refinado,
        })
        objetos.append(entrada)
    manifiesto = {
        "version": 1,
        "unidades": "m",
        "sistema_coordenadas": "+X craneal, +Y izquierda, +Z dorsal; origen en la abertura pélvica craneal, "
                               "plano medio y suelo de la cavidad a la altura del xifoides",
        "generador": f"Blender {bpy.app.version_string}",
        "vertice_diafragma": [round(c, 4) for c in ref.vertice_diafragma()],
        "objetos": objetos,
    }
    (salida / "manifiesto.json").write_text(json.dumps(manifiesto, ensure_ascii=False, indent=2), encoding="utf-8")
    informe = {"colisiones": colisiones, "desplazamientos_lineas_medias": desplazamientos,
               "ajustes_respecto_al_prompt": [{"objeto": a, "cambio": b, "motivo": c} for a, b, c in ajustes],
               "refinados": refinados}
    (salida / "informe_colisiones.json").write_text(json.dumps(informe, ensure_ascii=False, indent=2),
                                                    encoding="utf-8")
    (salida / "informe_colisiones.md").write_text(_informe_md(informe), encoding="utf-8")
    if con_glb:
        bpy.ops.export_scene.gltf(filepath=str(salida / "escena.glb"), export_format="GLB", export_extras=True,
                                  export_yup=True, export_apply=False)
        bpy.ops.wm.save_as_mainfile(filepath=str(salida / "cavidades_equinas.blend"), compress=True)
    log(f"Exportados {len(objetos)} objetos")


def exportar_contexto(salida, ctx):
    """Líneas medias relajadas (y esófago prolongado) para que el prototipador reproduzca el contexto."""
    def a_lista(curvas):
        return [{"curva": np.round(np.asarray(c), 5).tolist(), "radios": np.round(np.asarray(r), 5).tolist()}
                for c, r in curvas]
    datos = {"lineas": {oid: a_lista([cr])[0] for oid, cr in ctx.lineas.items()},
             "esofago_final": a_lista(ctx.esofago_final or [])}
    (Path(salida) / "contexto_organos.json").write_text(json.dumps(datos), encoding="utf-8")


def leer_contexto(dir_modelos):
    """Contexto de los órganos guardado por exportar_contexto (vacío si no existe)."""
    from anatomia.organos.contexto import Contexto

    ruta = Path(dir_modelos) / "contexto_organos.json"
    if not ruta.exists():
        return Contexto()
    datos = json.loads(ruta.read_text(encoding="utf-8"))
    lineas = {oid: (np.array(d["curva"]), np.array(d["radios"])) for oid, d in datos["lineas"].items()}
    esofago = [(np.array(d["curva"]), np.array(d["radios"])) for d in datos["esofago_final"]]
    return Contexto(lineas, esofago or None)


def _informe_md(inf):
    l = ["# Informe de colisiones y ajustes", "",
         "Generado automáticamente por `blender/generar_cavidades.py`. Profundidades en metros.", "",
         "## Interpenetraciones > 1 cm entre estructuras no contiguas", "",
         "| A | B | Profundidad inicial | Corregida | Deformado | Profundidad final |", "|---|---|---|---|---|---|"]
    for c in inf["colisiones"]:
        l.append(f"| {c['a']} | {c['b']} | {c['profundidad_inicial_m']} | {'sí' if c['corregida'] else 'no (' + c.get('motivo', 'sin mejora') + ': solo informe)'} "
                 f"| {c.get('deformado', '')} | {c.get('profundidad_final_m', '')} |")
    l += ["", "## Desplazamiento de las líneas medias digestivas tras la relajación", "",
          "| Objeto | Máximo (m) | Medio (m) |", "|---|---|---|"]
    for k, v in inf["desplazamientos_lineas_medias"].items():
        l.append(f"| {k} | {v['max_m']} | {v['medio_m']} |")
    l += ["", "## Ajustes respecto a las coordenadas del prompt", "", "| Objeto | Cambio | Motivo |", "|---|---|---|"]
    for a in inf["ajustes_respecto_al_prompt"]:
        l.append(f"| {a['objeto']} | {a['cambio']} | {a['motivo']} |")
    if inf["refinados"]:
        l += ["", "## Mallas refinadas", "", "| Objeto | Desviación del centroide (m) | Variación de volumen (%) |",
              "|---|---|---|"]
        for r in inf["refinados"]:
            l.append(f"| {r['objeto_id']} | {r['desviacion_centroide_m']} | {r['variacion_volumen_pct']} |")
    return "\n".join(l) + "\n"
