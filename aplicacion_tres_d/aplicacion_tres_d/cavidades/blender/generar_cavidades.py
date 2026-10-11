"""Generación procedural de las cavidades torácica, abdominal e inguinal del caballo.

Uso (desde la carpeta `cavidades/`):
    blender --background --python blender/generar_cavidades.py -- --salida modelos/

Opciones:
    --salida DIR        carpeta de salida (por defecto modelos/)
    --cavidades LISTA   subconjunto separado por comas: toracica,abdominal,pelvica,inguinal,referencia
    --sin-glb           no exportar escena.glb ni el .blend

Flujo por estructura: geometría base (tubo o campo implícito) -> remallado por
vóxeles (malla cerrada y manifold) -> suavizado de Taubin -> recorte a la
cavidad + suavizado Laplaciano -> triangulación y diezmado al presupuesto.
Después se miden y corrigen colisiones entre órganos (BVH) y se exporta.
Todo es determinista: mismas entradas, misma salida.
"""
import argparse
import sys
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
if str(RAIZ) not in sys.path:
    sys.path.insert(0, str(RAIZ))

import bmesh  # noqa: E402
import bpy  # noqa: E402
import numpy as np  # noqa: E402
from mathutils.bvhtree import BVHTree  # noqa: E402

from anatomia import catalogo as cat  # noqa: E402
from anatomia import especificacion as E  # noqa: E402
from anatomia import referencia as ref  # noqa: E402
from anatomia.organos import contexto as Ctx  # noqa: E402
from anatomia.organos import diafragma, estomago, rinones, tubos  # noqa: E402
from blender import exportar  # noqa: E402
from blender import materiales as M  # noqa: E402
from blender import primitivas as P  # noqa: E402

TRIS_OBJETIVO = 9000
TRIS_MAX = 15000
TRIS_MIN = 2000
TRIS_MAX_ENVOLTURA = 45000  # las envolturas son grandes y llevan relieve costal
MARGEN_CAVIDAD = 0.01

# Estado global de la ejecución (líneas medias relajadas, contexto de los órganos, informes).
ESTADO = {"lineas": {}, "desplazamientos": {}, "mallas": {}, "registro": [], "ctx": Ctx.Contexto(),
          "amoldados": set()}  # estructuras que ya tallan a sus vecinos en el campo


def log(msg):
    print(f"[cavidades] {msg}", flush=True)
    ESTADO["registro"].append(msg)


# ============================================================================
# Utilidades de Blender
# ============================================================================
def limpiar_escena():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    for col in list(bpy.data.collections):
        bpy.data.collections.remove(col)


def coleccion(nombre):
    col = bpy.data.collections.get(nombre)
    if col is None:
        col = bpy.data.collections.new(nombre)
        bpy.context.scene.collection.children.link(col)
    return col


def objeto_desde_malla(nombre, verts, caras, col=None):
    me = bpy.data.meshes.new(nombre)
    me.from_pydata(np.asarray(verts, dtype=float).tolist(), [], [tuple(int(i) for i in f) for f in caras])
    me.validate(clean_customdata=False)
    me.update()
    obj = bpy.data.objects.new(nombre, me)
    (col or bpy.context.scene.collection).objects.link(obj)
    return obj


def aplicar_modificador(obj, tipo, **props):
    mod = obj.modifiers.new("tmp", tipo)
    for k, v in props.items():
        setattr(mod, k, v)
    dg = bpy.context.evaluated_depsgraph_get()
    dg.update()
    nuevo = bpy.data.meshes.new_from_object(obj.evaluated_get(dg))
    obj.modifiers.remove(mod)
    viejo = obj.data
    obj.data = nuevo
    bpy.data.meshes.remove(viejo)


def malla_a_numpy(me):
    n = len(me.vertices)
    co = np.empty(n * 3)
    me.vertices.foreach_get("co", co)
    me.calc_loop_triangles()
    tris = np.empty(len(me.loop_triangles) * 3, dtype=np.int64)
    me.loop_triangles.foreach_get("vertices", tris)
    return co.reshape(-1, 3), tris.reshape(-1, 3)


def reemplazar_malla(obj, verts, tris):
    viejo = obj.data
    me = bpy.data.meshes.new(obj.name)
    me.from_pydata(np.asarray(verts).tolist(), [], np.asarray(tris).tolist())
    me.update()
    for mat in viejo.materials:  # conservar el material asignado
        me.materials.append(mat)
    obj.data = me
    bpy.data.meshes.remove(viejo)


def normales_y_manifold(obj):
    """Recalcula normales hacia fuera, elimina vértices sueltos e informa si la malla es manifold."""
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    sueltos = [v for v in bm.verts if not v.link_faces]
    if sueltos:
        bmesh.ops.delete(bm, geom=sueltos, context="VERTS")
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    manifold = all(e.is_manifold for e in bm.edges) and all(v.is_manifold for v in bm.verts)
    bm.to_mesh(obj.data)
    bm.free()
    obj.data.update()
    obj.data.shade_smooth()
    return manifold


def aristas_tris(tris):
    e = np.sort(np.vstack([tris[:, [0, 1]], tris[:, [1, 2]], tris[:, [2, 0]]]), axis=1)
    return np.unique(e, axis=0)


# Recorte a la cavidad (empuje según la normal del diafragma).
recortar = Ctx.recortar


# ============================================================================
# Materialización: malla base -> objeto final
# ============================================================================
def materializar(objeto_id, verts, caras, recorte, radio_min, remallar=True, taubin=4, voxel=None, tris_max=TRIS_MAX):
    meta = cat.POR_ID[objeto_id]
    col = coleccion(meta["coleccion"])
    obj = objeto_desde_malla(objeto_id, verts, caras, col)
    if remallar:
        area = P.area_malla(verts, caras)
        v = voxel or min(np.sqrt(2.0 * area / TRIS_OBJETIVO), radio_min / 2.2)
        v = max(v, 0.0012)
        aplicar_modificador(obj, "REMESH", mode="VOXEL", voxel_size=float(v), use_smooth_shade=True)
    V, T = malla_a_numpy(obj.data)
    if len(T) == 0:
        raise RuntimeError(f"{objeto_id}: malla vacía tras el remallado")
    A = aristas_tris(T)
    if taubin:
        V = P.suavizar_laplaciano(V, A, iteraciones=taubin, lam=0.5, mu=-0.53)
    if recorte:
        V2 = recortar(V, recorte)
        movidos = np.linalg.norm(V2 - V, axis=1) > 1e-6
        if np.any(movidos):
            V2 = P.suavizar_laplaciano(V2, A, iteraciones=2, lam=0.5)
            V2 = recortar(V2, recorte)
        V = V2
    reemplazar_malla(obj, V, T)
    if len(T) > tris_max:
        aplicar_modificador(obj, "DECIMATE", decimate_type="COLLAPSE", ratio=(tris_max * 0.97) / len(T))
        aplicar_modificador(obj, "TRIANGULATE")
    configurar_objeto(obj, meta)
    return obj


def configurar_objeto(obj, meta):
    manifold = normales_y_manifold(obj)
    for clave in ("objeto_id", "ficha_id", "cavidad", "sistema", "lado", "seleccionable", "capa",
                  "color_hex", "opacidad", "visible_por_defecto", "sexo_aplicable"):
        valor = meta[clave]
        obj[clave] = "" if valor is None else valor
    obj["manifold"] = bool(manifold)
    mat = M.crear_material(f"M_{meta['material']}_{int(meta['opacidad'] * 100)}", meta["color_hex"], meta["opacidad"])
    obj.data.materials.clear()
    obj.data.materials.append(mat)
    obj.color = (*M.hex_a_rgb(meta["color_hex"]), meta["opacidad"])
    ESTADO["mallas"][meta["objeto_id"]] = obj
    if not manifold:
        log(f"AVISO {meta['objeto_id']}: la malla no es manifold")


# ============================================================================
# Tubos
# ============================================================================
def tubo_parte(puntos, radios, haustras=None, anillos=None, curva=None, s_control=None, radios_puntos=None):
    """Malla de un tramo de tubo. Si se da `curva`, se usa tal cual (línea media relajada)."""
    r_ref = float(np.min(np.atleast_1d(radios))) if radios_puntos is None else float(np.min(radios_puntos))
    paso = min(0.008, max(0.0012, r_ref / 2.5))
    if haustras:
        paso = min(paso, haustras.get("periodo", 0.08) / 8)
    if anillos:
        paso = min(paso, anillos[1] / 6)
    if curva is None:
        c, s, sc = P.catmull_rom(puntos, paso=paso)
        r = P.perfil_radios(radios, s, sc)
    else:
        c, s = P.remuestrear(curva, paso)
        base = np.linspace(0.0, 1.0, len(radios_puntos))
        r = np.interp(s / max(s[-1], 1e-9), base, radios_puntos)
    lados = 32 if r.max() > 0.05 else 24 if r.max() > 0.015 else 14
    theta = np.linspace(0.0, 2 * np.pi, lados, endpoint=False)
    factor = None
    if haustras or anillos:
        h = haustras or {}
        factor = P.modulacion_haustras(s, theta, amplitud=h.get("amplitud", 0.0), periodo=h.get("periodo", 0.08),
                                       n_tenias=h.get("n_tenias", 0), anillos=anillos)
    v, f = P.malla_tubo(c, r, factor, n_lados=lados)
    return v, f, float(r.min())


def construir_tubo(objeto_id, spec, reemplazos=None):
    partes, rmin = [], 1.0
    for i, parte in enumerate(spec["partes"]):
        puntos = list(parte["puntos"])
        if reemplazos and i in reemplazos:
            puntos = reemplazos[i](puntos)
        v, f, r = tubo_parte(puntos, parte["radios"], parte.get("haustras"), parte.get("anillos"))
        partes.append((v, f))
        rmin = min(rmin, r)
    v, f = P.unir_mallas(partes)
    return materializar(objeto_id, v, f, spec["recorte"], rmin)


# ============================================================================
# Órganos macizos (campos implícitos de anatomia/organos)
# ============================================================================
campo_tubos = Ctx.campo_tubos
curvas_de_spec = Ctx.curvas_de_spec
restriccion_cavidad = Ctx.restriccion_cavidad


def organo_por_campo(objeto_id, funcion, minimo, maximo, h, sigma=1.0, recorte=None, radio_min=None, voxel=None,
                     tris_max=TRIS_MAX, taubin=4):
    v, f = P.superficie_por_campo(funcion, minimo, maximo, h, sigma=sigma)
    if len(f) == 0:
        raise RuntimeError(f"{objeto_id}: el campo no produjo superficie")
    return materializar(objeto_id, v, f, recorte, radio_min or 3 * h, voxel=voxel, tris_max=tris_max, taubin=taubin)


def construir_organo(objeto_id):
    """Malla una pieza de un módulo de anatomia/organos con el contexto actual."""
    ctx = ESTADO["ctx"]
    mod = Ctx.modulo(objeto_id)
    pz = ctx.pieza(objeto_id)
    minimo, maximo = pz["caja"]
    return organo_por_campo(objeto_id, lambda p: mod.campo(objeto_id, p, ctx), minimo, maximo, pz["h"],
                            sigma=pz.get("sigma", 1.0), recorte=pz.get("recorte"), radio_min=pz.get("radio_min"),
                            voxel=pz.get("voxel"), tris_max=pz.get("tris_max", TRIS_MAX), taubin=pz.get("taubin", 4))


# ============================================================================
# Cavidad torácica
# ============================================================================
def construir_toracica():
    log("Construyendo cavidad torácica…")
    for oid in ("TOR_CORAZON", "TOR_PERICARDIO"):
        construir_organo(oid)

    # Tubos mediastínicos (el esófago se completa tras relajar el abdomen).
    for oid in ("TOR_TRAQUEA", "TOR_AORTA", "TOR_VENA_CAVA_CRANEAL", "TOR_VENA_CAVA_CAUDAL"):
        construir_tubo(oid, E.TUBOS[oid])

    for oid in ("TOR_PULMON_DER", "TOR_PULMON_IZQ"):
        construir_organo(oid)

    construir_diafragma()

    # Mediastino: lámina translúcida en el plano medio.
    def f_med(p):
        x, y = p[:, 0], p[:, 1]
        c = np.abs(y - 0.005) - 0.006
        c = np.maximum(c, restriccion_cavidad(p, "toracica", 0.006))
        return np.maximum(c, x - 1.60)

    organo_por_campo("TOR_MEDIASTINO", f_med, (0.74, -0.02, -0.03), (1.62, 0.03, 0.72), 0.004, sigma=0.0,
                     recorte=None, voxel=0.004)


def _booleano(obj, verts, caras, operacion):
    """Aplica un booleano exacto con una malla cerrada auxiliar."""
    aux = objeto_desde_malla("booleano_tmp", verts, caras, obj.users_collection[0])
    normales_y_manifold(aux)
    mod = obj.modifiers.new("booleano", "BOOLEAN")
    mod.operation, mod.solver, mod.object = operacion, "EXACT", aux
    dg = bpy.context.evaluated_depsgraph_get()
    nuevo = bpy.data.meshes.new_from_object(obj.evaluated_get(dg))
    obj.modifiers.remove(mod)
    viejo = obj.data
    obj.data = nuevo
    bpy.data.meshes.remove(viejo)
    bpy.data.objects.remove(aux)


def construir_diafragma():
    """Cúpula diafragmática (anatomia/organos/diafragma.py): lámina, piezas sumadas y aberturas restadas."""
    meta = cat.POR_ID["TOR_DIAFRAGMA"]
    verts, caras = diafragma.malla()
    obj = objeto_desde_malla("TOR_DIAFRAGMA", verts, caras, coleccion(meta["coleccion"]))
    normales_y_manifold(obj)
    for uv, uf in diafragma.uniones():
        _booleano(obj, uv, uf, "UNION")
    cv, cf = diafragma.cortadores()
    _booleano(obj, cv, cf, "DIFFERENCE")
    aplicar_modificador(obj, "TRIANGULATE")
    n_tris = len(malla_a_numpy(obj.data)[1])
    if n_tris > TRIS_MAX:  # los pilares suman triángulos a la lámina
        aplicar_modificador(obj, "DECIMATE", decimate_type="COLLAPSE", ratio=(TRIS_MAX * 0.97) / n_tris)
        aplicar_modificador(obj, "TRIANGULATE")
    configurar_objeto(obj, meta)


# ============================================================================
# Cavidad abdominal
# ============================================================================
MACIZOS_ABDOMINALES = ("ABD_BAZO", "ABD_PANCREAS", "ABD_RINON_DER", "ABD_RINON_IZQ", "ABD_ADRENAL_DER",
                       "ABD_ADRENAL_IZQ")


def construir_macizos_abdominales():
    log("Construyendo órganos macizos abdominales…")
    for oid in MACIZOS_ABDOMINALES:
        construir_organo(oid)


def obstaculos_abdominales():
    """Campos de los órganos que los tubos digestivos deben rodear: (sdf, prioridad)."""
    ctx = ESTADO["ctx"]
    obs = [(ctx.sdf(oid), cat.POR_ID[oid]["prioridad"])
           for oid in ("ABD_HIGADO", "ABD_BAZO", "ABD_PANCREAS", "PEL_VEJIGA", "ABD_RINON_DER", "ABD_RINON_IZQ")]
    for oid in ("ABD_AORTA", "ABD_VENA_CAVA_CAUDAL", "ABD_A_MESENTERICA_CRANEAL"):
        obs.append((campo_tubos(curvas_de_spec(E.TUBOS[oid])), cat.POR_ID[oid]["prioridad"]))
    # Trayecto intraabdominal de las estructuras inguinales (vasos testiculares, deferente, canal).
    inguinales = []
    for lado in ("IZQ", "DER"):
        g = E.inguinal(lado)
        for clave in ("vasos", "deferente", "canal"):
            c, s, sc = P.catmull_rom(g[clave]["puntos"], paso=0.01)
            inguinales.append((c, P.perfil_radios(g[clave]["radios"], s, sc)))
    obs.append((campo_tubos(inguinales), cat.PRIORIDAD["inguinal"]))
    return obs


def relajar_cadenas():
    """Relaja las líneas medias de estómago, intestinos, ciego y colon (§4.6.2)."""
    log("Relajando líneas medias digestivas…")
    paso = 0.02
    pts, rad, pri, cad, arc, obj_idx, fijos = [], [], [], [], [], [], []
    segmentos = []
    for ic, cadena in enumerate(E.CADENAS):
        arco0 = 0.0
        for iseg, seg in enumerate(cadena["segmentos"]):
            if "generador" in seg:
                c, s = P.remuestrear(E.generador(seg["generador"]), paso)
                r = P.perfil_radios(seg["radios"], s, None)
            else:
                c, s, sc = P.catmull_rom(seg["puntos"], paso=paso)
                r = P.perfil_radios(seg["radios"], s, sc)
            r = r * E.ESCALA_RADIOS.get(seg["objeto_id"], 1.0)
            if iseg > 0:  # el punto de unión ya pertenece al segmento anterior
                c, s, r = c[1:], s[1:], r[1:]
            i0 = len(pts)
            pts.extend(c)
            # Radio efectivo: incluye el relieve de las haustras.
            amp = seg.get("haustras", {}).get("amplitud", 0.0)
            rad.extend(r * (1.0 + amp) + 0.003)
            pri.extend([cat.POR_ID[seg["objeto_id"]]["prioridad"]] * len(c))
            cad.extend([ic] * len(c))
            arc.extend(arco0 + s)
            obj_idx.extend([len(segmentos)] * len(c))
            fijos.extend([False] * len(c))
            segmentos.append({"spec": seg, "i0": i0, "i1": len(pts)})
            arco0 += s[-1] if len(s) else 0.0
    pts, rad, arc = np.array(pts), np.array(rad), np.array(arc)
    fijos, obj_idx = np.array(fijos), np.array(obj_idx)
    ids = [sg["spec"]["objeto_id"] for sg in segmentos]
    seg_de = {oid: segmentos[i] for i, oid in enumerate(ids)}
    # Anclajes: final del íleon (ostium ileal), inicio del colon ventral derecho y final del colon descendente.
    fijos[seg_de["ABD_ILEON"]["i1"] - 1] = True
    fijos[seg_de["ABD_COLON_VENTRAL_DER"]["i0"]] = True
    fijos[seg_de["ABD_COLON_DESCENDENTE"]["i1"] - 1] = True
    # Uniones anatómicas que no deben repelerse: íleon–ciego (ostium) y ciego–colon ventral derecho (orificio cecocólico).
    excl = np.zeros((len(pts), len(pts)), dtype=bool)
    ciego = np.zeros(len(pts), dtype=bool)
    ciego[seg_de["ABD_CIEGO"]["i0"]:seg_de["ABD_CIEGO"]["i1"]] = True
    sg = seg_de["ABD_ILEON"]
    ile_fin = np.zeros(len(pts), dtype=bool)
    ile_fin[sg["i0"]:sg["i1"]] = arc[sg["i0"]:sg["i1"]] > arc[sg["i1"] - 1] - 0.18
    sg = seg_de["ABD_COLON_VENTRAL_DER"]
    vd_ini = np.zeros(len(pts), dtype=bool)
    vd_ini[sg["i0"]:sg["i1"]] = arc[sg["i0"]:sg["i1"]] < arc[sg["i0"]] + 0.30
    for a in (ile_fin, vd_ini):
        excl |= a[:, None] & ciego[None, :]
        excl |= ciego[:, None] & a[None, :]
    original = pts.copy()

    restriccion = tubos.restriccion_relajacion

    t0 = time.time()
    relajado = P.relajar_tubos(pts, rad, np.array(pri, float), np.array(cad), arc, fijos, restriccion,
                               obstaculos=obstaculos_abdominales(), excluir=excl)
    log(f"Relajación completada en {time.time() - t0:.1f} s")
    for i, sgm in enumerate(segmentos):
        oid = ids[i]
        a, b = sgm["i0"], sgm["i1"]
        # Se incluye el último punto del segmento anterior para que los tramos se toquen.
        a_ext = a - 1 if a > 0 and cad[a - 1] == cad[a] else a
        amp = sgm["spec"].get("haustras", {}).get("amplitud", 0.0)
        ESTADO["lineas"][oid] = {"curva": relajado[a_ext:b], "radios": (rad[a_ext:b] - 0.003) / (1.0 + amp),
                                 "spec": sgm["spec"]}
        desp = np.linalg.norm(relajado[a:b] - original[a:b], axis=1)
        ESTADO["desplazamientos"][oid] = {"max_m": round(float(desp.max()), 3), "medio_m": round(float(desp.mean()), 3)}


def lineas_finales():
    """Líneas medias relajadas, suavizadas y con las asas separadas: {objeto_id: (curva, radios)}."""
    finales, r_ext = {}, {}
    for oid, linea in ESTADO["lineas"].items():
        finales[oid] = (P.suavizar_polilinea(linea["curva"], iteraciones=3, alfa=0.3), linea["radios"])
        # Radio exterior: incluye las crestas de las haustras.
        r_ext[oid] = linea["radios"] * (1.0 + linea["spec"].get("haustras", {}).get("amplitud", 0.0))
    for oid in finales:
        curva = finales[oid][0]
        antes = tubos.holgura_entre_asas(curva, r_ext[oid]) if tubos.en_meandro(oid) else np.inf
        if antes < 0.012:
            # Pasadas sucesivas: el suavizado de cada una frena la separación de las asas más apretadas.
            for _ in range(3):
                curva = tubos.separar_asas(curva, r_ext[oid], restriccion=tubos.restriccion_relajacion)
                if tubos.holgura_entre_asas(curva, r_ext[oid]) >= 0.012:
                    break
            finales[oid] = (curva, finales[oid][1])
            log(f"{oid}: asas separadas (holgura mínima {antes * 100:.1f} -> "
                f"{tubos.holgura_entre_asas(curva, r_ext[oid]) * 100:.1f} cm)")
    return finales


def caja_de(objeto_id):
    """Caja (mín, máx) de una estructura ya construida, en coordenadas del mundo."""
    V, _ = _datos(ESTADO["mallas"][objeto_id])
    return V.min(0), V.max(0)


def construir_tubos_digestivos():
    """Tubos digestivos como campos amoldados a los vecinos ya construidos (anatomia/organos/tubos.py).

    Se construyen de mayor a menor prioridad; cada uno talla a los anteriores y a los órganos macizos,
    de modo que las caras de contacto quedan aplanadas y la revisión de colisiones no tiene que
    apartar vértices a la fuerza.
    """
    log("Construyendo tubos digestivos…")
    ctx = ESTADO["ctx"]
    # Los órganos macizos se tallan aunque aún no tengan malla (el hígado se construye después): basta su forma.
    construidos = list(MACIZOS_ABDOMINALES) + ["ABD_HIGADO"]
    for oid in tubos.orden_de_construccion(list(ESTADO["lineas"])):
        if oid == "ABD_ESTOMAGO" and estomago.USAR_CAMPO:
            construir_organo(oid)  # órgano macizo (anatomia/organos/estomago.py)
            construidos.append(oid)
            continue
        pz = tubos.pieza(oid, ctx)
        mn, mx = pz["caja"]
        vecinos = []
        for v in construidos:
            if v in ESTADO["mallas"]:
                vmn, vmx = caja_de(v)
            else:
                vmn, vmx = (np.asarray(c, dtype=float) for c in ctx.pieza(v)["caja"])
            if np.all(vmn < mx) and np.all(vmx > mn):
                vecinos.append(v)
        organo_por_campo(oid, lambda p, oid=oid, vecinos=tuple(vecinos): tubos.campo(oid, p, ctx, vecinos), mn, mx,
                         pz["h"], sigma=pz["sigma"], recorte=pz["recorte"], radio_min=pz["radio_min"],
                         voxel=pz.get("voxel"))
        ESTADO["amoldados"].add(oid)
        construidos.append(oid)


def cardias_relajado():
    seg = E.CADENAS[0]["segmentos"][0]
    c0, s0, sc0 = P.catmull_rom(seg["puntos"], paso=0.02)
    linea = ESTADO["lineas"]["ABD_ESTOMAGO"]["curva"]
    frac = sc0[seg["cardias_indice"]] / s0[-1]
    return linea[int(round(frac * (len(linea) - 1)))]


def construir_abdominal():
    # La relajación solo usa la forma de los órganos macizos (obstáculos); sus mallas se construyen
    # después de los tubos para que puedan tallar las impresiones que declaran (p. ej., el bazo la del
    # estómago y el páncreas la del duodeno).
    relajar_cadenas()
    ESTADO["ctx"].lineas.update(lineas_finales())
    construir_tubos_digestivos()  # incluye el estómago
    construir_macizos_abdominales()
    # Esófago torácico prolongado hasta el cardias relajado.
    cardias = cardias_relajado()
    spec = {"recorte": E.TUBOS["TOR_ESOFAGO"]["recorte"], "partes": [dict(E.TUBOS["TOR_ESOFAGO"]["partes"][0])]}
    spec["partes"][0]["puntos"] = list(spec["partes"][0]["puntos"]) + [tuple(cardias)]
    construir_tubo("TOR_ESOFAGO", spec)
    ESTADO["ctx"].esofago_final = curvas_de_spec(spec)
    # El hígado se modela después del esófago para tallar su impresión esofágica.
    construir_organo("ABD_HIGADO")
    for oid in ("ABD_AORTA", "ABD_VENA_CAVA_CAUDAL", "ABD_A_MESENTERICA_CRANEAL"):
        construir_tubo(oid, E.TUBOS[oid])
    for oid, rid in (("ABD_URETER_DER", "ABD_RINON_DER"), ("ABD_URETER_IZQ", "ABD_RINON_IZQ")):
        construir_tubo(oid, E.TUBOS[oid], reemplazos={0: lambda pts, rid=rid: [tuple(rinones.hilio(rid))] + pts[1:]})


# ============================================================================
# Transición pélvica, región inguinal y referencias
# ============================================================================
def construir_pelvica():
    log("Construyendo transición pélvica…")
    construir_tubo("PEL_RECTO", E.TUBOS["PEL_RECTO"])
    construir_organo("PEL_VEJIGA")


def construir_inguinal():
    log("Construyendo región inguinal…")
    for lado in ("IZQ", "DER"):
        g = E.inguinal(lado)
        for clave, oid in (("anillo_profundo", "ING_ANILLO_PROFUNDO"), ("anillo_superficial", "ING_ANILLO_SUPERFICIAL")):
            t = g[clave]
            esc = t.get("escala_plano", (1.0, 1.0))
            ext = t["radio_mayor"] * max(esc) + t["radio_menor"] + 0.005
            organo_por_campo(f"{oid}_{lado}",
                             lambda p, t=t, esc=esc: P.sdf_toro(p, t["centro"], t["eje"], t["radio_mayor"],
                                                                t["radio_menor"], esc),
                             t["centro"] - ext, t["centro"] + ext, 0.0012, sigma=0.6, radio_min=t["radio_menor"])
        for clave, oid in (("canal", "ING_CANAL_INGUINAL"), ("cordon", "ING_CORDON_ESPERMATICO"),
                           ("deferente", "ING_CONDUCTO_DEFERENTE"), ("vasos", "ING_VASOS_TESTICULARES")):
            construir_tubo(f"{oid}_{lado}", {"recorte": None, "partes": [g[clave]]})
        # Testículo y epidídimo amoldado a él (anatomia/organos/inguinal.py).
        construir_organo(f"ING_TESTICULO_{lado}")
        construir_organo(f"ING_EPIDIDIMO_{lado}")


def construir_referencias():
    log("Construyendo referencias de orientación…")
    e = ref.EXPONENTE_SECCION
    # Envolvente.
    xs = np.linspace(ref.X_MIN_ENVOLVENTE, ref.X_MAX_ENVOLVENTE, 80)
    th = np.linspace(0, 2 * np.pi, 64, endpoint=False)
    cs, sn = np.cos(th), np.sin(th)
    sy = np.sign(cs) * np.abs(cs) ** (2 / e)
    sz = np.sign(sn) * np.abs(sn) ** (2 / e)
    secciones = []
    for x in xs:
        w, _, _ = ref.envolvente(x)
        zc, hz = ref.centro_y_semialtura(x)
        secciones.append(np.stack([np.full_like(th, x), w * sy, zc + hz * sz], axis=1))
    V = np.vstack(secciones + [[xs[0], 0, ref.centro_y_semialtura(xs[0])[0]],
                               [xs[-1], 0, ref.centro_y_semialtura(xs[-1])[0]]])
    n, m = len(xs), len(th)
    F = []
    for i in range(n - 1):
        for j in range(m):
            F.append((i * m + j, i * m + (j + 1) % m, (i + 1) * m + (j + 1) % m, (i + 1) * m + j))
    for j in range(m):
        F.append((n * m, (j + 1) % m, j))
        F.append((n * m + 1, (n - 1) * m + j, (n - 1) * m + (j + 1) % m))
    obj = objeto_desde_malla("REF_ENVOLVENTE", V, F, coleccion("Referencias"))
    configurar_objeto(obj, cat.POR_ID["REF_ENVOLVENTE"])

    def punto_pared(x, z, lado, holgura=0.006):
        w, _, _ = ref.envolvente(x)
        zc, hz = ref.centro_y_semialtura(x)
        u = np.clip(np.abs((z - zc) / hz), 0, 1)
        return np.stack([x, lado * (w * (1 - u ** e) ** (1 / e) + holgura), z], axis=-1)

    # Costillas: desde el techo hasta el esternón (1–8) o hasta el arco costal (9–18).
    partes = []
    for i in range(1, ref.NUM_COSTILLAS + 1):
        x = float(ref.x_costilla(i))
        zc, hz = ref.centro_y_semialtura(x)
        z_dor = zc + 0.97 * hz
        z_ven = max(zc - 0.97 * hz, 0.0) if i <= ref.NUM_COSTILLAS_ESTERNALES else \
            0.05 + (i - 9) / 9 * 0.40
        zs = np.linspace(z_dor, z_ven, 30)
        for lado in (1, -1):
            pts = punto_pared(np.full_like(zs, x), zs, lado)
            v, f = P.malla_tubo(pts, np.full(len(pts), 0.004), n_lados=6, anillos_tapa=1)
            partes.append((v, f))
    v, f = P.unir_mallas(partes)
    obj = objeto_desde_malla("REF_ARCOS_COSTALES", v, f, coleccion("Referencias"))
    configurar_objeto(obj, cat.POR_ID["REF_ARCOS_COSTALES"])
    # Líneas topográficas.
    partes = []
    for nombre, z in ref.LINEAS_TOPOGRAFICAS.items():
        xs = np.linspace(0.10, 1.60, 120)
        _, zv, zd = ref.envolvente(xs)
        ok = (z > zv + 0.01) & (z < zd - 0.01)
        for lado in (1, -1):
            pts = punto_pared(xs[ok], np.full(ok.sum(), z), lado, 0.01)
            v, f = P.malla_tubo(pts, np.full(len(pts), 0.003), n_lados=6, anillos_tapa=1)
            partes.append((v, f))
    v, f = P.unir_mallas(partes)
    obj = objeto_desde_malla("REF_LINEAS_TOPOGRAFICAS", v, f, coleccion("Referencias"))
    configurar_objeto(obj, cat.POR_ID["REF_LINEAS_TOPOGRAFICAS"])


# ============================================================================
# Envolturas de cavidad (modelos inmersivos)
# ============================================================================
def surcos_costales(x, y, z, costillas=range(1, ref.NUM_COSTILLAS + 1)):
    """Intensidad (0…1) de las impresiones costales sobre la pared lateral del molde de la cavidad."""
    cfg = E.ENVOLTURAS
    w, _, zd = ref.envolvente(x)
    acc = np.zeros(len(x))
    for i in costillas:
        xr = ref.x_costilla(i) + cfg["oblicuidad_costal"] * (z - 0.35)
        z_ven = 0.0 if i <= ref.NUM_COSTILLAS_ESTERNALES else 0.05 + (i - 9) / 9 * 0.40
        en_z = 1.0 / (1.0 + np.exp(-(z - z_ven) / 0.015)) * 1.0 / (1.0 + np.exp((z - (zd - 0.03)) / 0.015))
        acc += np.exp(-((x - xr) / cfg["ancho_surco_costal"]) ** 2) * en_z
    lateral = np.clip(np.abs(y) / np.maximum(w, 1e-3) * 1.6 - 0.3, 0.0, 1.0)  # no en el plano medio
    cerca_pared = np.exp(-(ref.distancia_envolvente(x, y, z) / 0.02) ** 2)
    return np.clip(acc, 0, 1) * lateral * cerca_pared


def construir_envoltura(cavidad):
    oid = cat.envoltura_de(cavidad)
    if oid is None:
        return
    log(f"Construyendo envoltura {oid}…")
    prof = E.ENVOLTURAS["profundidad_surco_costal"]
    if cavidad == "toracica":
        def f(p):
            x, y, z = p[:, 0], p[:, 1], p[:, 2]
            n = ref.normal_diafragma(y, z)
            F = np.maximum(ref.distancia_envolvente(x, y, z), (ref.x_diafragma(y, z) - x) * n[:, 0])
            F = np.maximum(F, x - 1.625)
            return F + prof * surcos_costales(x, y, z)
        organo_por_campo(oid, f, (0.74, -0.33, -0.06), (1.64, 0.33, 0.73), 0.007, sigma=1.0, voxel=0.006,
                         tris_max=TRIS_MAX_ENVOLTURA, taubin=3)
    elif cavidad == "abdominal":
        def f(p):
            x, y, z = p[:, 0], p[:, 1], p[:, 2]
            n = ref.normal_diafragma(y, z)
            F = np.maximum(ref.distancia_envolvente(x, y, z), (x - ref.x_diafragma(y, z)) * n[:, 0])
            F = np.maximum(F, 0.10 - x)
            zc, _ = ref.centro_y_semialtura(x)
            linea_alba = 0.004 * np.exp(-(y / 0.012) ** 2) * (z < zc)
            return F + prof * surcos_costales(x, y, z, range(8, ref.NUM_COSTILLAS + 1)) + linea_alba
        organo_por_campo(oid, f, (0.08, -0.33, -0.06), (1.38, 0.33, 0.73), 0.008, sigma=1.0, voxel=0.007,
                         tris_max=TRIS_MAX_ENVOLTURA, taubin=3)
    elif cavidad == "inguinal":
        cfg_p, cfg_e = E.ENVOLTURAS["pared_inguinal"], E.ENVOLTURAS["escroto"]

        def f(p):
            x, y, z = p[:, 0], p[:, 1], p[:, 2]
            partes = []
            for lado in ("IZQ", "DER"):
                g = E.inguinal(lado)
                t = g["testiculo"]
                ang = np.radians(t["inclinacion_grados"])
                rot = np.array([[np.cos(ang), 0, -np.sin(ang)], [0, 1, 0], [np.sin(ang), 0, np.cos(ang)]])
                semi = np.asarray(t["semiejes"]) + cfg_e["holgura"]
                partes.append(P.sdf_elipsoide(p, t["centro"], semi, rot))
                c, s, sc = P.catmull_rom(g["cordon"]["puntos"][1:], paso=0.01)
                partes.append(P.sdf_capsulas(p, c, cfg_e["radio_cuello"]))
            # Saco escrotal único: puente central entre los dos lóbulos.
            zt = E.Z_TESTICULO
            partes.append(P.sdf_elipsoide(p, (0.10, 0.0, zt + 0.01), (0.07, 0.055, 0.055)))
            F = partes[0]
            for q in partes[1:]:
                F = P.smin(F, q, k=0.04)
            F = F + 0.005 * np.exp(-(y / 0.01) ** 2) * (z < E.Z_TESTICULO + 0.03)  # rafe escrotal
            # Pared abdominal ventral caudal, engrosada alrededor del conducto inguinal.
            d = ref.distancia_envolvente(np.clip(x, ref.X_MIN_ENVOLVENTE, None), y, z)
            grosor = cfg_p["grosor"] + cfg_p["engrosamiento"] * np.exp(
                -((x - 0.22) / 0.06) ** 2 - ((np.abs(y) - 0.10) / 0.05) ** 2)
            zc, _ = ref.centro_y_semialtura(np.clip(x, ref.X_MIN_ENVOLVENTE, None))
            # Placa de contorno redondeado (superelipse en x, y) con los bordes romos.
            xc, ax = 0.5 * (cfg_p["x"][0] + cfg_p["x"][1]), 0.5 * (cfg_p["x"][1] - cfg_p["x"][0])
            contorno = ((np.abs(x - xc) / ax) ** 3 + (np.abs(y) / cfg_p["y_max"]) ** 3) ** (1 / 3) - 1.0
            pared = np.maximum(-d, d - grosor)
            pared = -P.smin(-pared, -contorno * min(ax, cfg_p["y_max"]), 0.03)
            pared = -P.smin(-pared, -(z - zc), 0.03)
            return P.smin(F, pared, k=0.03)
        organo_por_campo(oid, f, (0.0, -0.25, -0.17), (0.42, 0.25, 0.40), 0.006, sigma=1.0, voxel=0.005,
                         tris_max=TRIS_MAX_ENVOLTURA, taubin=3)


# ============================================================================
# Colisiones (§4.6.2)
# ============================================================================
CLASES_VOLUMETRICAS = {85, 75, 70, 65, 60, 55, 50, 45, 40, 35, 30, 95}
UNIONES = cat.UNIONES


def _datos(obj):
    V, T = malla_a_numpy(obj.data)
    mw = np.array(obj.matrix_world)
    V = V @ mw[:3, :3].T + mw[:3, 3]
    return V, T


def _dentro(bvh, p):
    """Prueba de paridad con rayos (independiente de la orientación de las normales).

    Se lanza un rayo en +X; si el número de cortes es impar se confirma con +Y y +Z (mayoría).
    """
    from mathutils import Vector

    def paridad(d):
        o, n = Vector(p), 0
        for _ in range(64):
            hit, _, _, _ = bvh.ray_cast(o, d)
            if hit is None:
                break
            n += 1
            o = hit + d * 1e-5
        return n % 2

    if not paridad(Vector((1.0, 0.0, 0.0))):
        return False
    return paridad(Vector((0.0, 1.0, 0.0))) + paridad(Vector((0.0, 0.0, 1.0))) >= 1


def _penetracion(Va, bvh_b, muestras=2500):
    """Profundidad de cada vértice de A dentro de B (0 si está fuera); se muestrea uniformemente."""
    idx = np.linspace(0, len(Va) - 1, min(muestras, len(Va))).astype(int)
    prof = np.zeros(len(Va))
    for i in idx:
        if _dentro(bvh_b, Va[i]):
            loc, _, _, dist = bvh_b.find_nearest(Va[i])
            prof[i] = dist if loc is not None else 0.0
    return prof


def _abrir_hueco(V, T, Vh, holgura=0.004):
    """Aparta los vértices de V alrededor de cada vértice de Vh que haya quedado dentro de V."""
    from mathutils.kdtree import KDTree

    mn, mx = V.min(0) - 0.01, V.max(0) + 0.01
    cand = np.nonzero(np.all((Vh > mn) & (Vh < mx), axis=1))[0]
    if len(cand) == 0:
        return V
    bvh_v = BVHTree.FromPolygons(V.tolist(), T.tolist())
    kd = KDTree(len(V))
    for i, co in enumerate(V):
        kd.insert(co, i)
    kd.balance()
    desp = np.zeros_like(V)
    for i in cand:
        if not _dentro(bvh_v, Vh[i]):
            continue
        _, _, _, prof = bvh_v.find_nearest(Vh[i])
        radio = prof + 0.02
        for co, j, dist in kd.find_range(Vh[i], radio):
            d = V[j] - Vh[i]
            n = np.linalg.norm(d)
            if n > 1e-9:
                empuje = (prof + holgura) * (1.0 - dist / radio) * d / n
                if np.dot(empuje, empuje) > np.dot(desp[j], desp[j]):
                    desp[j] = empuje
    return V + desp


def revisar_colisiones(corregir=True, umbral=0.01):
    log("Revisando colisiones entre estructuras…")
    objs = {oid: o for oid, o in ESTADO["mallas"].items()
            if cat.POR_ID[oid]["cavidad"] != "referencia" and not cat.POR_ID[oid]["envoltura"]
            and oid not in ("TOR_PERICARDIO", "TOR_MEDIASTINO")}
    datos = {oid: _datos(o) for oid, o in objs.items()}
    bvh = {oid: BVHTree.FromPolygons(V.tolist(), T.tolist()) for oid, (V, T) in datos.items()}
    caja = {oid: (V.min(0), V.max(0)) for oid, (V, _) in datos.items()}
    informe = []
    ids = sorted(objs)
    for i, a in enumerate(ids):
        for b in ids[i + 1:]:
            if frozenset((a, b)) in UNIONES:
                continue
            (amin, amax), (bmin, bmax) = caja[a], caja[b]
            if np.any(amin > bmax) or np.any(bmin > amax):
                continue
            pa = _penetracion(datos[a][0], bvh[b]).max()
            pb = _penetracion(datos[b][0], bvh[a]).max()
            prof = max(pa, pb)
            if prof <= umbral:
                continue
            ma, mb = cat.POR_ID[a], cat.POR_ID[b]
            vol = ma["prioridad"] in CLASES_VOLUMETRICAS and mb["prioridad"] in CLASES_VOLUMETRICAS
            entrada = {"a": a, "b": b, "profundidad_inicial_m": round(float(prof), 4), "corregida": False}
            amoldado = bool({a, b} & ESTADO["amoldados"])
            if amoldado:
                # Las vísceras amoldadas en el campo no se deforman a posteriori: apartar vértices a la
                # fuerza deja parches rasgados. El solape residual queda en el informe.
                entrada["motivo"] = "amoldado en el campo"
            elif not (vol or "TOR_DIAFRAGMA" in (a, b)):
                entrada["motivo"] = "conducto fino"
            if corregir and not amoldado and (vol or "TOR_DIAFRAGMA" in (a, b)):
                bajo, alto = (a, b) if ma["prioridad"] < mb["prioridad"] else (b, a)
                if ma["prioridad"] == mb["prioridad"]:
                    bajo, alto = sorted((a, b))
                V, T = datos[bajo]
                V_original = V.copy()
                A = aristas_tris(T)
                for it in range(4):
                    prof_v = _penetracion(V, bvh[alto], muestras=len(V))
                    dentro = prof_v > 0
                    if not np.any(dentro):
                        break
                    for k in np.nonzero(dentro)[0]:
                        loc, _, _, _ = bvh[alto].find_nearest(V[k])
                        sale = np.array(loc) - V[k]  # desde dentro hacia la superficie = hacia fuera
                        V[k] = np.array(loc) + 0.004 * sale / max(np.linalg.norm(sale), 1e-9)
                    # Caso inverso: bordes del órgano prioritario metidos en el otro. Se apartan
                    # los vértices cercanos del órgano deformable con una caída lineal.
                    V = _abrir_hueco(V, T, datos[alto][0])
                    if it < 3:
                        vecinos = np.zeros(len(V), dtype=bool)
                        vecinos[dentro] = True
                        vecinos[A[dentro[A[:, 0]] | dentro[A[:, 1]]].ravel()] = True
                        V = P.suavizar_laplaciano(V, A, iteraciones=1, lam=0.5, mascara=vecinos)
                cav = cat.POR_ID[bajo]["cavidad"]
                V = recortar(V, cav if cav in ("abdominal", "toracica") else None)
                bvh_nuevo = BVHTree.FromPolygons(V.tolist(), T.tolist())
                final = max(_penetracion(V, bvh[alto]).max(), _penetracion(datos[alto][0], bvh_nuevo).max())
                if final < prof:
                    reemplazar_malla(objs[bajo], V, T)
                    normales_y_manifold(objs[bajo])
                    datos[bajo] = (V, T)
                    bvh[bajo] = bvh_nuevo
                    caja[bajo] = (V.min(0), V.max(0))
                    entrada.update({"corregida": True, "deformado": bajo, "profundidad_final_m": round(float(final), 4)})
                else:  # la corrección no mejora: se conserva la malla original
                    datos[bajo] = (V_original, T)
                    entrada.update({"deformado": "", "profundidad_final_m": round(float(prof), 4)})
            informe.append(entrada)
    return informe


# ============================================================================
# Programa principal
# ============================================================================
def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--salida", default=str(RAIZ / "modelos"))
    ap.add_argument("--cavidades", default="toracica,abdominal,pelvica,inguinal,referencia")
    ap.add_argument("--sin-glb", action="store_true")
    args = ap.parse_args(argv)
    salida = Path(args.salida)
    if not salida.is_absolute():
        salida = (Path.cwd() / salida).resolve()
    cavidades = args.cavidades.split(",")
    t0 = time.time()
    limpiar_escena()
    for nombre in ("Toracica", "Abdominal", "Pelvica_referencia", "Inguinal", "Referencias"):
        coleccion(nombre)
    if "toracica" in cavidades:
        construir_toracica()
        construir_envoltura("toracica")
    if "abdominal" in cavidades:
        construir_abdominal()
        construir_envoltura("abdominal")
    if "pelvica" in cavidades:
        construir_pelvica()
    if "inguinal" in cavidades:
        construir_inguinal()
        construir_envoltura("inguinal")
    if "referencia" in cavidades:
        construir_referencias()
    colisiones = revisar_colisiones()
    for oid, o in ESTADO["mallas"].items():
        o.hide_set(not cat.POR_ID[oid]["visible_por_defecto"])
    exportar.exportar_todo(salida, ESTADO["mallas"], colisiones, ESTADO["desplazamientos"], E.AJUSTES,
                           con_glb=not args.sin_glb)
    exportar.exportar_contexto(salida, ESTADO["ctx"])
    log(f"Generación completa en {time.time() - t0:.1f} s: {len(ESTADO['mallas'])} objetos en {salida}")


if __name__ == "__main__":
    main()
