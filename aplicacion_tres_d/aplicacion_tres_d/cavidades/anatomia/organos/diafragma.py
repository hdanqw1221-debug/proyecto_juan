"""Diafragma: lámina sobre la superficie x = x_diafragma(y, z), recortada por la envolvente, con pilares.

Especificación de la forma (Sisson y Grossman; Dyce–Sack–Wensing; Budras, Anatomy of the Horse;
docs/investigacion_anatomica_cavidades_equinas.md §2.8):
- Cúpula muy oblicua con el vértice en el 6.º espacio intercostal (referencia.x_diafragma).
- Centro tendinoso delgado (~5 mm) en la parte central de la cúpula; porción muscular periférica
  (costal y esternal) más gruesa (~14 mm) hacia la inserción en la pared.
- Pilares (porción lumbar): bandas musculares aplanadas que salen de la parte dorsal de la cúpula a cada
  lado de la aorta y corren caudodorsalmente bajo las vértebras lumbares, adelgazándose hacia sus
  tendones: el derecho, mayor, hasta L3–L4; el izquierdo, hasta L2. Entre ellos pasa la aorta (hiato
  aórtico); quedan dorsales a la vena cava caudal y mediales a riñones y adrenales.
- Tres aberturas: hiatos aórtico y esofágico y foramen de la vena cava.

A diferencia de los demás órganos se construye como malla explícita (es una lámina fina):
    malla()        -> (vértices, caras) de la lámina cerrada (cara craneal, caudal y borde).
    uniones()      -> [(vértices, caras)] mallas cerradas que se suman con booleano (pilares).
    cortadores()   -> (vértices, caras) mallas cerradas que se restan con booleano (aberturas).
El generador de Blender aplica los booleanos; el prototipador muestra las piezas sin aplicarlos.
"""
import numpy as np

from anatomia import referencia as ref
from blender import primitivas as P

GROSOR = {
    "tendinoso": 0.005,    # centro tendinoso (m)
    "muscular": 0.014,     # porción muscular periférica (m)
    "transicion": (0.40, 0.72),  # radio relativo de la cúpula entre el que se pasa de uno a otro
}

# Pilares: puntos de la línea media (de la cúpula hacia caudal), semiancho (transversal) y semigrosor
# (dorsoventral). El primer punto queda dentro de la lámina para que la unión booleana sea limpia.
PILARES = {
    "derecho": {"y": -0.050, "z0": 0.615,
                "puntos": [(0.760, -0.050, 0.648), (0.700, -0.047, 0.661), (0.630, -0.042, 0.664),
                           (0.565, -0.037, 0.661)],
                "semiancho": [0.028, 0.024, 0.018, 0.012, 0.007],
                "semigrosor": [0.010, 0.010, 0.008, 0.006, 0.004]},
    "izquierdo": {"y": 0.058, "z0": 0.612,
                  "puntos": [(0.762, 0.058, 0.648), (0.700, 0.055, 0.661), (0.645, 0.052, 0.660)],
                  "semiancho": [0.022, 0.019, 0.013, 0.007],
                  "semigrosor": [0.009, 0.008, 0.006, 0.004]},
}


def _grosor(rho_rel):
    """Grosor de la lámina según el radio relativo en la cúpula (0 centro, 1 inserción)."""
    a, b = GROSOR["transicion"]
    t = np.clip((rho_rel - a) / (b - a), 0.0, 1.0)
    t = t * t * (3 - 2 * t)
    return GROSOR["tendinoso"] + (GROSOR["muscular"] - GROSOR["tendinoso"]) * t


def malla():
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
    rel = np.arange(1, K + 1) / K
    rho = rel[:, None] * largo[None, :]
    Y = np.concatenate([[c_y], (c_y + rho * np.cos(ang)).ravel()])
    Z = np.concatenate([[c_z], (c_z + rho * np.sin(ang)).ravel()])
    g = np.concatenate([[_grosor(0.0)], np.repeat(_grosor(rel), M_ang)])
    X = ref.x_diafragma(Y, Z)
    Pm = np.stack([X, Y, Z], axis=1)
    n = ref.normal_diafragma(Y, Z)
    sup, inf = Pm + 0.5 * g[:, None] * n, Pm - 0.5 * g[:, None] * n
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
    return verts, caras


def _pilar(cfg, n_lados=14):
    """Banda de sección elíptica a lo largo de la línea media del pilar (malla cerrada)."""
    y, z0 = cfg["y"], cfg["z0"]
    inicio = (float(ref.x_diafragma(y, z0)) + 0.002, y, z0)  # dentro de la lámina
    c, s, sc = P.catmull_rom([inicio] + list(cfg["puntos"]), paso=0.008)
    a = P.perfil_radios(cfg["semigrosor"], s, sc)  # según la normal del marco (≈ dorsoventral)
    b = P.perfil_radios(cfg["semiancho"], s, sc)   # según la binormal (≈ transversal)
    th = np.linspace(0.0, 2 * np.pi, n_lados, endpoint=False)
    factor = (a * b)[:, None] / np.sqrt((b[:, None] * np.cos(th)) ** 2 + (a[:, None] * np.sin(th)) ** 2)
    return P.malla_tubo(c, np.ones(len(c)), factor, n_lados=n_lados, anillos_tapa=3)


def uniones():
    return [_pilar(cfg) for cfg in PILARES.values()]


def cortadores():
    """Cilindros orientados según la normal local en cada abertura (hiatos aórtico y esofágico, foramen de la cava)."""
    cortes = []
    for nombre, (y, z, r) in ref.ABERTURAS_DIAFRAGMA.items():
        p0 = np.array(ref.punto_abertura(nombre))
        nn = ref.normal_diafragma(y, z)
        cortes.append(P.malla_tubo(np.array([p0 - 0.05 * nn, p0 + 0.05 * nn]), np.array([r, r]), n_lados=24,
                                   anillos_tapa=1))
    return P.unir_mallas(cortes)


def forma(objeto_id, p):
    """Distancia aproximada a la lámina (para vecinos que quieran apartarse de ella)."""
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    n = ref.normal_diafragma(y, z)
    d = np.abs((x - ref.x_diafragma(y, z)) * n[:, 0]) - 0.5 * GROSOR["muscular"]
    return np.maximum(d, ref.distancia_envolvente(x, y, z))
