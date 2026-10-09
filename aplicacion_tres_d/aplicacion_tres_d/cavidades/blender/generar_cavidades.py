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
from blender import exportar  # noqa: E402
from blender import materiales as M  # noqa: E402
from blender import primitivas as P  # noqa: E402

TRIS_OBJETIVO = 9000
TRIS_MAX = 15000
TRIS_MIN = 2000
MARGEN_CAVIDAD = 0.01

# Estado global de la ejecución (líneas medias relajadas, informes).
ESTADO = {"lineas": {}, "desplazamientos": {}, "mallas": {}, "registro": []}


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


# ============================================================================
# Recorte a la cavidad (empuje según la normal del diafragma)
# ============================================================================
def recortar(V, modo, margen=MARGEN_CAVIDAD):
    if modo is None:
        return V
    V = np.array(V, copy=True)
    for _ in range(3):
        x = np.clip(V[:, 0], ref.X_MIN_ENVOLVENTE, ref.X_MAX_ENVOLVENTE)
        V[:, 1], V[:, 2] = ref.proyectar_dentro_envolvente(x, V[:, 1], V[:, 2], margen)
        if modo in ("abdominal", "toracica"):
            xd = ref.x_diafragma(V[:, 1], V[:, 2])
            n = ref.normal_diafragma(V[:, 1], V[:, 2])
            d = (xd - V[:, 0]) * n[:, 0]  # > 0 caudal al diafragma
            if modo == "abdominal":
                falta = np.maximum(margen - d, 0.0)
                V -= falta[:, None] * n
            else:
                falta = np.maximum(margen + d, 0.0)
                V += falta[:, None] * n
    return V


# ============================================================================
# Materialización: malla base -> objeto final
# ============================================================================
def materializar(objeto_id, verts, caras, recorte, radio_min, remallar=True, taubin=4, voxel=None):
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
    if len(T) > TRIS_MAX:
        aplicar_modificador(obj, "DECIMATE", decimate_type="COLLAPSE", ratio=(TRIS_MAX * 0.97) / len(T))
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
# Campos implícitos de órganos macizos
# ============================================================================
def campo_corazon(p, separacion=0.0):
    C = E.CORAZON
    B, A = np.array(C["base"]), np.array(C["apice"])
    eje = A - B
    L = np.linalg.norm(eje)
    u = eje / L
    lat = np.array([0.0, 1.0, 0.0]) - u[1] * u
    lat /= np.linalg.norm(lat)
    otro = np.cross(u, lat)
    q = p - B
    a = q @ u
    t = a / L
    rl, ro = q @ lat, q @ otro
    rho = np.sqrt((rl / C["aplanamiento_lateral"]) ** 2 + ro ** 2)
    tc = np.clip(t, 1e-4, 1 - 1e-4)
    perfil = C["radio_max"] * tc ** 0.35 * (1 - tc) ** 0.65 / 0.524
    fuera_eje = np.where(t < 0, -a, np.where(t > 1, a - L, 0.0))
    F = np.where((t > 0) & (t < 1), rho - perfil, np.sqrt(rho ** 2 + fuera_eje ** 2))
    for centro, radio in C["auriculas"]:
        F = P.smin(F, np.linalg.norm(p - np.array(centro), axis=1) - radio, k=0.05)
    return F - separacion


def campo_tubos(partes_curvas):
    """SDF de la unión de varias cadenas de cápsulas [(curva, radios)]."""
    def f(p):
        return np.min([P.sdf_capsulas(p, c, r) for c, r in partes_curvas], axis=0)
    return f


def curvas_de_spec(spec):
    salida = []
    for parte in spec["partes"]:
        c, s, sc = P.catmull_rom(parte["puntos"], paso=0.01)
        salida.append((c, P.perfil_radios(parte["radios"], s, sc)))
    return salida


def restriccion_cavidad(p, cavidad, margen=MARGEN_CAVIDAD):
    """Campo (>0 fuera) que limita un órgano a su cavidad."""
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    c = ref.distancia_envolvente(x, y, z) + margen
    n = ref.normal_diafragma(y, z)
    d = (ref.x_diafragma(y, z) - x) * n[:, 0]
    if cavidad == "abdominal":
        c = np.maximum(c, margen - d)
    elif cavidad == "toracica":
        c = np.maximum(c, margen + d)
    return c


def organo_por_campo(objeto_id, funcion, minimo, maximo, h, sigma=1.0, recorte=None, radio_min=None, voxel=None):
    v, f = P.superficie_por_campo(funcion, minimo, maximo, h, sigma=sigma)
    if len(f) == 0:
        raise RuntimeError(f"{objeto_id}: el campo no produjo superficie")
    return materializar(objeto_id, v, f, recorte, radio_min or 3 * h, voxel=voxel)


# ============================================================================
# Cavidad torácica
# ============================================================================
def construir_toracica():
    log("Construyendo cavidad torácica…")
    # Corazón y pericardio.
    bb_min, bb_max = (1.30, -0.16, 0.0), (1.63, 0.18, 0.52)

    def f_corazon(p):
        return np.maximum(campo_corazon(p), restriccion_cavidad(p, "toracica", 0.012))

    organo_por_campo("TOR_CORAZON", f_corazon, bb_min, bb_max, 0.006, sigma=1.2, recorte="toracica")

    def f_peri(p):
        return np.maximum(campo_corazon(p, E.PERICARDIO_SEPARACION), restriccion_cavidad(p, "toracica", 0.005))

    organo_por_campo("TOR_PERICARDIO", f_peri, bb_min, bb_max, 0.006, sigma=1.2, recorte="toracica")

    # Tubos mediastínicos (el esófago se completa tras relajar el abdomen).
    for oid in ("TOR_TRAQUEA", "TOR_AORTA", "TOR_VENA_CAVA_CRANEAL", "TOR_VENA_CAVA_CAUDAL"):
        construir_tubo(oid, E.TUBOS[oid])

    # Pulmones.
    tubos_med = []
    for oid in ("TOR_TRAQUEA", "TOR_ESOFAGO", "TOR_AORTA", "TOR_VENA_CAVA_CRANEAL", "TOR_VENA_CAVA_CAUDAL"):
        tubos_med += curvas_de_spec(E.TUBOS[oid])
    sdf_med = campo_tubos(tubos_med)
    cfg = E.PULMON

    def f_pulmon(lado):
        s = 1.0 if lado == "IZQ" else -1.0

        def f(p):
            x, y, z = p[:, 0], p[:, 1], p[:, 2]
            lim = np.maximum(ref.x_diafragma(y, z) + 0.012, ref.x_borde_basal_pulmon(z))
            c = np.maximum(ref.distancia_envolvente(x, y, z) + 0.008, x - cfg["x_max"])
            c = np.maximum(c, lim - x)
            med = cfg["mediastino"] - s * y
            if lado == "DER":
                acc = cfg["lobulo_accesorio"]
                med = np.minimum(med, P.sdf_caja(p, acc["min"], acc["max"]))
            c = np.maximum(c, med)
            c = np.maximum(c, cfg["dilatacion_corazon"] - campo_corazon(p))
            if lado == "IZQ":
                esc = cfg["escotadura_izq"]
                c = np.maximum(c, -P.sdf_caja(p, esc["min"], esc["max"]))
            c = np.maximum(c, cfg["dilatacion_tubos"] - sdf_med(p))
            return c
        return f

    for lado in ("DER", "IZQ"):
        ymin, ymax = (-0.33, 0.05) if lado == "DER" else (0.0, 0.33)
        organo_por_campo(f"TOR_PULMON_{lado}", f_pulmon(lado), (0.74, ymin, -0.03), (1.60, ymax, 0.72), 0.01,
                         sigma=1.0, recorte="toracica")

    construir_diafragma()

    # Mediastino: lámina translúcida en el plano medio.
    def f_med(p):
        x, y = p[:, 0], p[:, 1]
        c = np.abs(y - 0.005) - 0.006
        c = np.maximum(c, restriccion_cavidad(p, "toracica", 0.006))
        return np.maximum(c, x - 1.60)

    organo_por_campo("TOR_MEDIASTINO", f_med, (0.74, -0.02, -0.03), (1.62, 0.03, 0.72), 0.004, sigma=0.0,
                     recorte=None, voxel=0.004)


def construir_diafragma():
    """Cúpula diafragmática: lámina de 1 cm sobre x = f(y, z), recortada por la envolvente, con 3 aberturas."""
    M_ang, K = 120, 26
    c_y, c_z = 0.0, 0.33
    ang = np.linspace(0.0, 2 * np.pi, M_ang, endpoint=False)
    largo = np.zeros(M_ang)
    for i, a in enumerate(ang):
        lo, hi = 0.0, 0.9
        for _ in range(40):
            mid = 0.5 * (lo + hi)
            y, z = c_y + mid * np.cos(a), c_z + mid * np.sin(a)
            dentro = ref.valor_envolvente(ref.x_diafragma(y, z), y, z, 0.002) <= 1.0
            lo, hi = (mid, hi) if dentro else (lo, mid)
        largo[i] = lo
    rho = (np.arange(1, K + 1) / K)[:, None] * largo[None, :]
    Y = np.concatenate([[c_y], (c_y + rho * np.cos(ang)).ravel()])
    Z = np.concatenate([[c_z], (c_z + rho * np.sin(ang)).ravel()])
    X = ref.x_diafragma(Y, Z)
    Pm = np.stack([X, Y, Z], axis=1)
    n = ref.normal_diafragma(Y, Z)
    sup, inf = Pm + 0.005 * n, Pm - 0.005 * n
    nv = len(Pm)
    verts = np.vstack([sup, inf])
    idx = lambda k, j: 1 + k * M_ang + (j % M_ang)  # noqa: E731
    caras = []
    for j in range(M_ang):
        caras.append((0, idx(0, j), idx(0, j + 1)))
        caras.append((nv, nv + idx(0, j + 1), nv + idx(0, j)))
    for k in range(K - 1):
        for j in range(M_ang):
            q = (idx(k, j), idx(k + 1, j), idx(k + 1, j + 1), idx(k, j + 1))
            caras.append(q)
            caras.append(tuple(nv + i for i in q[::-1]))
    for j in range(M_ang):
        a, b = idx(K - 1, j), idx(K - 1, j + 1)
        caras.append((a, nv + a, nv + b, b))
    meta = cat.POR_ID["TOR_DIAFRAGMA"]
    obj = objeto_desde_malla("TOR_DIAFRAGMA", verts, caras, coleccion(meta["coleccion"]))
    normales_y_manifold(obj)
    # Aberturas: cilindros orientados según la normal local, restados con booleano exacto.
    cortes = []
    for nombre, (y, z, r) in ref.ABERTURAS_DIAFRAGMA.items():
        p0 = np.array(ref.punto_abertura(nombre))
        nn = ref.normal_diafragma(y, z)
        cortes.append(P.malla_tubo(np.array([p0 - 0.05 * nn, p0 + 0.05 * nn]), np.array([r, r]), n_lados=24,
                                   anillos_tapa=1))
    cv, cf = P.unir_mallas(cortes)
    cortador = objeto_desde_malla("cortador_tmp", cv, cf, coleccion(meta["coleccion"]))
    normales_y_manifold(cortador)
    mod = obj.modifiers.new("aberturas", "BOOLEAN")
    mod.operation, mod.solver, mod.object = "DIFFERENCE", "EXACT", cortador
    dg = bpy.context.evaluated_depsgraph_get()
    nuevo = bpy.data.meshes.new_from_object(obj.evaluated_get(dg))
    obj.modifiers.remove(mod)
    viejo = obj.data
    obj.data = nuevo
    bpy.data.meshes.remove(viejo)
    bpy.data.objects.remove(cortador)
    aplicar_modificador(obj, "TRIANGULATE")
    configurar_objeto(obj, meta)


# ============================================================================
# Cavidad abdominal
# ============================================================================
def sdf_higado(p):
    cfg = E.HIGADO
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    huella = np.full(len(p), 1.0)
    grosor = np.zeros(len(p))
    for y0, z0, ay, az, k in cfg["lobulos"]:
        e2 = ((y - y0) / ay) ** 2 + ((z - z0) / az) ** 2
        huella = np.minimum(huella, (np.sqrt(e2) - 1.0) * min(ay, az))
        grosor = np.maximum(grosor, k * cfg["grosor_max"] * np.sqrt(np.clip(1.0 - e2, 0.0, 1.0)))
    grosor = np.maximum(grosor, cfg["grosor_min"])
    dfdy, dfdz = ref.gradiente_diafragma(y, z)
    estira = np.sqrt(1.0 + dfdy ** 2 + dfdz ** 2)
    x_frente = ref.x_diafragma(y, z) - cfg["separacion"] * estira
    x_dorso = x_frente - grosor * estira
    lamina = np.maximum(x - x_frente, x_dorso - x) / estira
    return np.maximum(np.maximum(huella, lamina), ref.distancia_envolvente(x, y, z) + 0.01)


def campo_higado_final(p):
    """Hígado con impresiones: renal y adrenal derechas, surco de la vena cava y esófago abdominal."""
    F = sdf_higado(p)
    F = np.maximum(F, 0.006 - sdf_rinon("ABD_RINON_DER", p))
    r = E.ADRENALES["ABD_ADRENAL_DER"]
    F = np.maximum(F, 0.004 - P.sdf_elipsoide(p, r["centro"], r["semiejes"]))
    tubos = curvas_de_spec(E.TUBOS["ABD_VENA_CAVA_CAUDAL"]) + ESTADO.get("esofago_final", [])
    return np.maximum(F, 0.004 - campo_tubos(tubos)(p))


def _marco(t, n):
    t = t / np.linalg.norm(t)
    n = n - np.dot(n, t) * t
    n /= np.linalg.norm(n)
    return np.stack([t, np.cross(n, t), n], axis=1)  # columnas: largo, ancho, grosor


def sdf_bazo(p):
    cfg = E.BAZO
    base, ext = np.array(cfg["base"]), np.array(cfg["extremo"])
    medio = (base + ext) / 2 + np.array([-0.03, 0.0, 0.0])
    c, s, _ = P.catmull_rom([base, medio, ext], paso=0.02)
    F = np.full(len(p), 1.0)
    e = ref.EXPONENTE_SECCION
    for i, (q, si) in enumerate(zip(c, s)):
        x, z = q[0], q[2]
        w, _, _ = ref.envolvente(x)
        zc, hz = ref.centro_y_semialtura(x)
        u = min(abs((z - zc) / hz), 0.98)
        y_pared = w * (1 - u ** e) ** (1 / e)
        n = np.array([0.0, e * y_pared ** (e - 1) / w ** e, e * u ** (e - 1) / hz * np.sign(z - zc)])
        n /= np.linalg.norm(n)
        # Centro del elemento: sobre la pared izquierda, retirado según su normal.
        centro = np.array([x, y_pared, z]) - (0.012 + cfg["semigrosor"]) * n
        tang = c[min(i + 1, len(c) - 1)] - c[max(i - 1, 0)]
        R = _marco(tang, n)
        f = si / s[-1]
        ancho = cfg["ancho"][0] + (cfg["ancho"][1] - cfg["ancho"][0]) * f
        F = np.minimum(F, P.sdf_elipsoide(p, centro, (0.03, ancho, cfg["semigrosor"]), R))
    return np.maximum(F, restriccion_cavidad(p, "abdominal", 0.01))


def sdf_rinon(objeto_id, p):
    r = E.RINONES[objeto_id]
    c = np.array(r["centro"])
    a = np.array(r["semiejes"])
    medial = -np.sign(c[1])
    if r["forma"] == "cordiforme":
        F = P.sdf_elipsoide(p, c + (0.015, 0.03, 0), (a[0] * 0.75, a[1] * 0.6, a[2]))
        F = P.smin(F, P.sdf_elipsoide(p, c + (0.015, -0.03, 0), (a[0] * 0.75, a[1] * 0.6, a[2])), 0.02)
        F = P.smin(F, P.sdf_elipsoide(p, c + (-0.025, 0, 0), (a[0] * 0.75, a[1] * 0.62, a[2] * 0.93)), 0.02)
        hilio = c + (0.0, medial * a[1] * 1.0, 0.0)
        F = np.maximum(F, -(np.linalg.norm(p - hilio, axis=1) - 0.018))
    else:
        F = P.sdf_elipsoide(p, c, a)
        hilio = c + (0.0, medial * a[1] * 1.05, 0.0)
        F = np.maximum(F, -(np.linalg.norm(p - hilio, axis=1) - 0.028))
    return F


def sdf_pancreas(p):
    cfg = E.PANCREAS
    c, s, sc = P.catmull_rom(cfg["puntos"], paso=0.01)
    r = P.perfil_radios(cfg["radios"], s, sc)
    centro_z = np.mean(np.array(cfg["puntos"])[:, 2])
    q = p.copy()
    q[:, 2] = centro_z + (p[:, 2] - centro_z) / cfg["aplanamiento_z"]
    c2 = c.copy()
    c2[:, 2] = centro_z + (c[:, 2] - centro_z) / cfg["aplanamiento_z"]
    return P.sdf_capsulas(q, c2, r) * cfg["aplanamiento_z"]


def sdf_vejiga(p):
    v = E.VEJIGA
    return P.sdf_elipsoide(p, v["centro"], v["semiejes"])


def construir_macizos_abdominales():
    log("Construyendo órganos macizos abdominales…")
    organo_por_campo("ABD_BAZO", sdf_bazo, (0.60, 0.08, 0.12), (1.14, 0.33, 0.72), 0.006, sigma=1.0,
                     recorte="abdominal")

    def f_panc(p):
        F = np.maximum(sdf_pancreas(p), restriccion_cavidad(p, "abdominal"))
        return np.maximum(F, 0.004 - sdf_higado(p))

    organo_por_campo("ABD_PANCREAS", f_panc, (0.72, -0.20, 0.44), (0.96, 0.13, 0.63), 0.005, sigma=1.0,
                     recorte="abdominal")
    for oid, r in E.RINONES.items():
        c = np.array(r["centro"])
        organo_por_campo(oid, lambda p, oid=oid: sdf_rinon(oid, p), c - 0.11, c + 0.11, 0.004, sigma=1.0,
                         recorte="abdominal")
    for oid, r in E.ADRENALES.items():
        c, a = np.array(r["centro"]), np.array(r["semiejes"])
        organo_por_campo(oid, lambda p, c=c, a=a: P.sdf_elipsoide(p, c, a), c - a - 0.01, c + a + 0.01, 0.0015,
                         sigma=0.8, recorte="abdominal", radio_min=0.006)


def obstaculos_abdominales():
    """Campos de los órganos que los tubos digestivos deben rodear: (sdf, prioridad)."""
    obs = [
        (sdf_higado, cat.POR_ID["ABD_HIGADO"]["prioridad"]),
        (sdf_bazo, cat.POR_ID["ABD_BAZO"]["prioridad"]),
        (sdf_pancreas, cat.POR_ID["ABD_PANCREAS"]["prioridad"]),
        (sdf_vejiga, cat.POR_ID["PEL_VEJIGA"]["prioridad"]),
    ]
    for oid in E.RINONES:
        obs.append((lambda p, oid=oid: sdf_rinon(oid, p), cat.POR_ID[oid]["prioridad"]))
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

    def restriccion(Pp, R):
        Q = Pp.copy()
        for _ in range(2):
            x = np.clip(Q[:, 0], ref.X_MIN_ENVOLVENTE, ref.X_MAX_ENVOLVENTE)
            Q[:, 1], Q[:, 2] = ref.proyectar_dentro_envolvente(x, Q[:, 1], Q[:, 2], R + 0.01)
            n = ref.normal_diafragma(Q[:, 1], Q[:, 2])
            d = (ref.x_diafragma(Q[:, 1], Q[:, 2]) - Q[:, 0]) * n[:, 0]
            falta = np.maximum(R + 0.014 - d, 0.0)
            Q -= falta[:, None] * n
        return Q

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


def construir_tubos_digestivos():
    log("Construyendo tubos digestivos…")
    for oid, linea in ESTADO["lineas"].items():
        spec = linea["spec"]
        curva = P.suavizar_polilinea(linea["curva"], iteraciones=3, alfa=0.3)
        v, f, rmin = tubo_parte(None, None, spec.get("haustras"), curva=curva, radios_puntos=linea["radios"])
        materializar(oid, v, f, "abdominal", rmin)


def cardias_relajado():
    seg = E.CADENAS[0]["segmentos"][0]
    c0, s0, sc0 = P.catmull_rom(seg["puntos"], paso=0.02)
    linea = ESTADO["lineas"]["ABD_ESTOMAGO"]["curva"]
    frac = sc0[seg["cardias_indice"]] / s0[-1]
    return linea[int(round(frac * (len(linea) - 1)))]


def construir_abdominal():
    construir_macizos_abdominales()
    relajar_cadenas()
    construir_tubos_digestivos()
    # Esófago torácico prolongado hasta el cardias relajado.
    cardias = cardias_relajado()
    spec = {"recorte": E.TUBOS["TOR_ESOFAGO"]["recorte"], "partes": [dict(E.TUBOS["TOR_ESOFAGO"]["partes"][0])]}
    spec["partes"][0]["puntos"] = list(spec["partes"][0]["puntos"]) + [tuple(cardias)]
    construir_tubo("TOR_ESOFAGO", spec)
    ESTADO["esofago_final"] = curvas_de_spec(spec)
    # El hígado se modela después del esófago para tallar su impresión esofágica.
    organo_por_campo("ABD_HIGADO", campo_higado_final, (0.66, -0.31, -0.02), (1.34, 0.22, 0.72), 0.008,
                     sigma=1.2, recorte="abdominal")
    for oid in ("ABD_AORTA", "ABD_VENA_CAVA_CAUDAL", "ABD_A_MESENTERICA_CRANEAL"):
        construir_tubo(oid, E.TUBOS[oid])
    for oid, rid in (("ABD_URETER_DER", "ABD_RINON_DER"), ("ABD_URETER_IZQ", "ABD_RINON_IZQ")):
        construir_tubo(oid, E.TUBOS[oid], reemplazos={0: lambda pts, rid=rid: [tuple(E.hilio_renal(rid))] + pts[1:]})


# ============================================================================
# Transición pélvica, región inguinal y referencias
# ============================================================================
def construir_pelvica():
    log("Construyendo transición pélvica…")
    construir_tubo("PEL_RECTO", E.TUBOS["PEL_RECTO"])
    v = E.VEJIGA
    c, a = np.array(v["centro"]), np.array(v["semiejes"])
    organo_por_campo("PEL_VEJIGA", sdf_vejiga, c - a - 0.01, c + a + 0.01, 0.005, sigma=0.8)


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
                           ("deferente", "ING_CONDUCTO_DEFERENTE"), ("vasos", "ING_VASOS_TESTICULARES"),
                           ("epididimo", "ING_EPIDIDIMO")):
            construir_tubo(f"{oid}_{lado}", {"recorte": None, "partes": [g[clave]]})
        t = g["testiculo"]
        ang = np.radians(t["inclinacion_grados"])
        rot = np.array([[np.cos(ang), 0, -np.sin(ang)], [0, 1, 0], [np.sin(ang), 0, np.cos(ang)]])
        c = np.asarray(t["centro"])
        organo_por_campo(f"ING_TESTICULO_{lado}", lambda p, c=c, rot=rot, t=t: P.sdf_elipsoide(p, c, t["semiejes"], rot),
                         c - 0.07, c + 0.07, 0.003, sigma=0.8)


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
# Colisiones (§4.6.2)
# ============================================================================
CLASES_VOLUMETRICAS = {85, 75, 70, 65, 60, 55, 50, 45, 40, 35, 30, 95}
UNIONES = {frozenset(p) for p in [
    ("ABD_ESTOMAGO", "ABD_DUODENO"), ("ABD_DUODENO", "ABD_YEYUNO"), ("ABD_YEYUNO", "ABD_ILEON"),
    ("ABD_ILEON", "ABD_CIEGO"), ("ABD_CIEGO", "ABD_COLON_VENTRAL_DER"),
    ("ABD_COLON_VENTRAL_DER", "ABD_FLEXURA_ESTERNAL"), ("ABD_FLEXURA_ESTERNAL", "ABD_COLON_VENTRAL_IZQ"),
    ("ABD_COLON_VENTRAL_IZQ", "ABD_FLEXURA_PELVICA"), ("ABD_FLEXURA_PELVICA", "ABD_COLON_DORSAL_IZQ"),
    ("ABD_COLON_DORSAL_IZQ", "ABD_FLEXURA_DIAFRAGMATICA"), ("ABD_FLEXURA_DIAFRAGMATICA", "ABD_COLON_DORSAL_DER"),
    ("ABD_COLON_DORSAL_DER", "ABD_COLON_TRANSVERSO"), ("ABD_COLON_TRANSVERSO", "ABD_COLON_DESCENDENTE"),
    ("ABD_COLON_DESCENDENTE", "PEL_RECTO"), ("TOR_ESOFAGO", "ABD_ESTOMAGO"), ("TOR_CORAZON", "TOR_PERICARDIO"),
    ("ABD_URETER_DER", "ABD_RINON_DER"), ("ABD_URETER_IZQ", "ABD_RINON_IZQ"),
    ("ABD_URETER_DER", "PEL_VEJIGA"), ("ABD_URETER_IZQ", "PEL_VEJIGA"),
    ("TOR_AORTA", "TOR_CORAZON"), ("TOR_VENA_CAVA_CRANEAL", "TOR_CORAZON"), ("TOR_VENA_CAVA_CAUDAL", "TOR_CORAZON"),
    ("TOR_AORTA", "ABD_AORTA"), ("TOR_VENA_CAVA_CAUDAL", "ABD_VENA_CAVA_CAUDAL"),
    ("ABD_A_MESENTERICA_CRANEAL", "ABD_AORTA"),
    # Los bronquios principales penetran en el pulmón por el hilio.
    ("TOR_TRAQUEA", "TOR_PULMON_DER"), ("TOR_TRAQUEA", "TOR_PULMON_IZQ"),
    # Estructuras que atraviesan las aberturas del diafragma.
    ("TOR_AORTA", "TOR_DIAFRAGMA"), ("ABD_AORTA", "TOR_DIAFRAGMA"), ("TOR_ESOFAGO", "TOR_DIAFRAGMA"),
    ("TOR_VENA_CAVA_CAUDAL", "TOR_DIAFRAGMA"), ("ABD_VENA_CAVA_CAUDAL", "TOR_DIAFRAGMA"),
]}


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
            if cat.POR_ID[oid]["cavidad"] != "referencia" and oid not in ("TOR_PERICARDIO", "TOR_MEDIASTINO")}
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
            if corregir and (vol or "TOR_DIAFRAGMA" in (a, b)):
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
    if "abdominal" in cavidades:
        construir_abdominal()
    if "pelvica" in cavidades:
        construir_pelvica()
    if "inguinal" in cavidades:
        construir_inguinal()
    if "referencia" in cavidades:
        construir_referencias()
    colisiones = revisar_colisiones()
    for oid, o in ESTADO["mallas"].items():
        o.hide_set(not cat.POR_ID[oid]["visible_por_defecto"])
    exportar.exportar_todo(salida, ESTADO["mallas"], colisiones, ESTADO["desplazamientos"], E.AJUSTES,
                           con_glb=not args.sin_glb)
    log(f"Generación completa en {time.time() - t0:.1f} s: {len(ESTADO['mallas'])} objetos en {salida}")


if __name__ == "__main__":
    main()
