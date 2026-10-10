"""Riñones (derecho cordiforme, izquierdo reniforme) y glándulas adrenales, retroperitoneales.

Especificación de la forma (Bradley 1922, fig. 65 y p. 141; Sisson y Grossman; Dyce–Sack–Wensing;
docs/investigacion_anatomica_cavidades_equinas.md §3.10):
- Ambos riñones, aplicados contra el techo del abdomen con los psoas entre medias (cara dorsal a
  ~5 cm del techo), aplanados dorsoventralmente (5–6 cm), cápsula lisa, borde lateral delgado y
  borde medial grueso y redondeado con el hilio: escotadura con el seno renal abierto hacia
  medial (y algo ventral), donde nace el uréter (hilio()).
- Riñón derecho: cordiforme («corazón de naipe»), ~15 × 14 cm en vista dorsal; ventral a los
  extremos dorsales de las dos últimas costillas y a la primera apófisis transversa lumbar, más
  craneal que el izquierdo. Polo craneal ancho y redondeado (impresión renal del hígado), lóbulo
  craneomedial prominente, extremo caudal más estrecho y delgado. Cara dorsal convexa (diafragma y
  psoas a partes iguales: su parte craneal se amolda con suavidad al diafragma dentro del campo),
  cara ventral aplanada y algo excavada (hígado, páncreas, base del ciego).
- Riñón izquierdo: reniforme (habichuela), más largo y estrecho (~17,5 × 11 cm) y más caudal
  (última costilla a L2–L3). Polo craneal más estrecho (su ángulo craneomedial lo ocupa la
  adrenal), polo caudal ancho; cara dorsal con dos vertientes suaves separadas por una cresta
  longitudinal baja (surco entre los psoas mayor y menor), cara ventral convexa.
- Adrenales: lengüetas alargadas, aplanadas y algo curvas (~8,5 × 3,5 × 1,3 cm) craneomediales a
  cada riñón; la derecha entre el borde craneomedial del riñón y la vena cava caudal, la izquierda
  entre el polo craneal del riñón y la aorta, craneal a la a. mesentérica craneal. Se amoldan al
  riñón y al vaso vecinos.
Construcción del riñón: contorno en vista dorsal (Catmull-Rom cerrada) -> distancia 2D tabulada ->
lente con semigrosores dorsal y ventral que crecen con la profundidad bajo el borde -> superficie
media curvada y orientada -> seno renal -> intersección suave con la cavidad abdominal.
"""
import numpy as np

from anatomia.organos import contexto as C
from blender import primitivas as P

# Riñones: coordenadas locales u (eje mayor, hacia craneal), v (hacia medial), w (normal dorsal).
PARAMETROS = {
    "ABD_RINON_DER": {
        "centro": (0.746, -0.158, 0.600),     # centro del contorno sobre la superficie media (m)
        "angulos": (12.0, 8.0, 0.0),          # cabeceo (polo craneal a ventral), alabeo (borde lateral
                                              # a ventral) y giro (polo craneal a medial), en grados
        # Contorno en vista dorsal, (u, v) en cm (Bradley 1922, fig. 65): lóbulo craneomedial,
        # escotadura del hilio y borde caudomedial que se retira para dejar salir al uréter.
        "contorno": [(7.3, 1.0), (6.7, 3.9), (5.4, 5.9), (3.8, 7.0), (2.2, 7.1), (1.0, 6.3), (0.3, 5.0),
                     (-0.2, 4.2), (-0.9, 4.2), (-1.9, 4.6), (-3.6, 4.7), (-5.6, 3.7), (-7.0, 1.5), (-7.2, -0.9),
                     (-6.2, -3.6), (-3.6, -6.2), (-0.2, -7.2), (3.2, -7.0), (5.6, -5.4), (6.9, -2.6)],
        "escala": 1.04,                       # factor del contorno
        "grosor_dorsal": (0.020, 0.031),      # semigrosor dorsal máximo junto al borde lateral y al medial
        "grosor_ventral": (0.016, 0.025),
        "borde_dorsal": (0.060, 0.040),       # anchura del redondeo del borde (lateral, medial): grande = convexa
        "borde_ventral": (0.035, 0.024),      # cara ventral más plana
        "curvatura": (3.2, 1.6),              # flecha de la superficie media w0 = -(cu u² + cv v²) / 2 (1/m)
        "excavacion_ventral": (0.004, 0.005, 0.0, 0.045),  # (profundidad, u0, v0, radio) de la cara ventral
        "cresta_dorsal": None,
        "seno": {"centro": (-0.004, 0.039, -0.004), "semiejes": (0.012, 0.016, 0.008)},  # (u, v, w) m
        "hilio": (-0.006, 0.050, -0.008),     # origen del uréter (u, v, w respecto a la superficie media)
    },
    "ABD_RINON_IZQ": {
        "centro": (0.662, 0.104, 0.598),
        "angulos": (2.0, 7.0, 0.0),
        # Habichuela: polo craneal estrecho (adrenal craneomedial), hilio a media longitud, polo caudal ancho.
        "contorno": [(8.8, -0.6), (8.3, 1.6), (6.9, 3.0), (4.8, 3.9), (2.6, 5.1), (1.3, 5.3), (0.6, 4.2),
                     (0.0, 2.9), (-0.8, 3.0), (-1.5, 4.5), (-2.5, 5.5), (-4.6, 5.8), (-6.8, 5.2), (-8.3, 3.3),
                     (-8.8, 0.4), (-8.2, -2.6), (-6.2, -4.8), (-3.0, -5.6), (0.8, -5.6), (4.3, -4.9),
                     (7.0, -3.4)],
        "grosor_dorsal": (0.022, 0.030),
        "grosor_ventral": (0.020, 0.027),
        "borde_dorsal": (0.030, 0.024),       # cara dorsal plana (psoas)
        "borde_ventral": (0.050, 0.040),      # cara ventral convexa
        "curvatura": (0.6, 0.0),
        "excavacion_ventral": None,
        "cresta_dorsal": (0.004, 0.16),       # (v de la cresta, pendiente de cada vertiente)
        "seno": {"centro": (-0.004, 0.022, -0.006), "semiejes": (0.013, 0.015, 0.008)},
        "hilio": (-0.006, 0.047, -0.022),
    },
}

ADRENALES = {
    # Eje a (largo, hacia craneal), b (ancho), c (grosor); la lámina se inclina con el alabeo.
    "ABD_ADRENAL_DER": {"centro": (0.818, -0.084, 0.580), "angulos": (12.0, 8.0, 0.0),
                        "semiejes": (0.045, 0.017, 0.0068), "curvatura": -8.0, "afilado": 0.30,
                        "rinon": "ABD_RINON_DER"},  # riñón al que se amolda
    "ABD_ADRENAL_IZQ": {"centro": (0.733, 0.050, 0.604), "angulos": (4.0, 10.0, 0.0),
                        "semiejes": (0.044, 0.017, 0.0065), "curvatura": -8.0, "afilado": 0.30,
                        "rinon": "ABD_RINON_IZQ"},
}

FORMA_ADRENAL = {
    "exponente_contorno": 2.7,  # contorno de lengüeta de extremos romos (2 = elipse)
    "exponente_grosor": 2.6,    # caras aplanadas con bordes romos
}

AJUSTE = {
    "margen_cavidad": 0.012,   # distancia a la pared y al diafragma (> MARGEN_CAVIDAD del recorte)
    "suavidad_cavidad": 0.012,  # radio de la intersección suave con la cavidad
    "holgura_vaso": 0.004,     # separación con la vena cava y la aorta
    "holgura_vecino": 0.003,   # separación con páncreas y bazo
    "impronta_max": 0.008,     # profundidad máxima de esas impresiones
    "holgura_adrenal": 0.003,  # separación adrenal–riñón
}


# ----------------------------------------------------------------------------
# Utilidades geométricas
# ----------------------------------------------------------------------------
def smax(a, b, k):
    """Intersección suave (el complemento de primitivas.smin)."""
    return -P.smin(-a, -b, k)


def _marco(angulos, medial):
    """Ejes locales (columnas u, v, w) a partir de cabeceo, alabeo y giro; `medial` = signo de y medial."""
    cab, alab, giro = np.radians(angulos)
    eu = np.array([np.cos(cab), 0.0, -np.sin(cab)])
    ew = np.array([np.sin(cab), 0.0, np.cos(cab)])
    ev = np.array([0.0, float(medial), 0.0])
    ev, ew = np.cos(alab) * ev + np.sin(alab) * ew, -np.sin(alab) * ev + np.cos(alab) * ew
    eu, ev = np.cos(giro) * eu + np.sin(giro) * ev, -np.sin(giro) * eu + np.cos(giro) * ev
    return np.stack([eu, ev, ew], axis=1)


def _catmull_rom_cerrada(puntos, muestras=24):
    """Polígono denso de una Catmull-Rom cerrada que pasa por `puntos` (2D)."""
    p = np.asarray(puntos, dtype=float)
    n = len(p)
    t = np.linspace(0.0, 1.0, muestras, endpoint=False)[:, None]
    tramos = []
    for i in range(n):
        p0, p1, p2, p3 = p[(i - 1) % n], p[i], p[(i + 1) % n], p[(i + 2) % n]
        tramos.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t ** 2
                             + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    return np.vstack(tramos)


def _tabla_distancia(poligono, paso=0.001, margen=0.03, bloque=3000):
    """Distancia 2D con signo (< 0 dentro) al polígono cerrado, tabulada en una rejilla regular."""
    a = np.asarray(poligono, dtype=float)
    b = np.roll(a, -1, axis=0)
    ab = b - a
    l2 = np.maximum(np.sum(ab * ab, axis=1), 1e-12)
    mn, mx = a.min(0) - margen, a.max(0) + margen
    eu = np.arange(mn[0], mx[0] + paso, paso)
    ev = np.arange(mn[1], mx[1] + paso, paso)
    U, V = np.meshgrid(eu, ev, indexing="ij")
    q = np.stack([U.ravel(), V.ravel()], axis=1)
    d = np.empty(len(q))
    dentro = np.empty(len(q), dtype=bool)
    dv = np.where(np.abs(ab[:, 1]) > 1e-12, ab[:, 1], 1e-12)
    for i in range(0, len(q), bloque):
        qq = q[i:i + bloque, None, :]
        t = np.clip(np.sum((qq - a) * ab, axis=2) / l2, 0.0, 1.0)
        d[i:i + bloque] = np.linalg.norm(qq - (a + t[..., None] * ab), axis=2).min(axis=1)
        cruza = (a[:, 1] > qq[..., 1]) != (b[:, 1] > qq[..., 1])
        u_corte = a[:, 0] + (qq[..., 1] - a[:, 1]) * ab[:, 0] / dv
        dentro[i:i + bloque] = np.sum(cruza & (qq[..., 0] < u_corte), axis=1) % 2 == 1
    return np.where(dentro, -d, d).reshape(U.shape), mn, paso


def _consultar(tabla, u, v):
    """Interpolación bilineal de la tabla; fuera de ella suma la distancia al borde de la tabla."""
    S, mn, paso = tabla
    fu, fv = (u - mn[0]) / paso, (v - mn[1]) / paso
    cu = np.clip(fu, 0.0, S.shape[0] - 1.000001)
    cv = np.clip(fv, 0.0, S.shape[1] - 1.000001)
    i, j = cu.astype(np.int64), cv.astype(np.int64)
    a, b = cu - i, cv - j
    val = (S[i, j] * (1 - a) * (1 - b) + S[i + 1, j] * a * (1 - b)
           + S[i, j + 1] * (1 - a) * b + S[i + 1, j + 1] * a * b)
    return val + np.hypot(fu - cu, fv - cv) * paso


def _perfil(x):
    """Perfil elíptico del borde: 0 en el contorno, 1 a la profundidad de redondeo."""
    x = np.clip(x, 0.0, 1.0)
    return np.sqrt(1.0 - (1.0 - x) ** 2)


def _sdf_elipsoide(q, semiejes):
    """Distancia aproximada a un elipsoide centrado en coordenadas locales (k0 (k0 - 1) / k1)."""
    k0 = np.linalg.norm(q / semiejes, axis=1)
    k1 = np.linalg.norm(q / np.square(semiejes), axis=1)
    return k0 * (k0 - 1.0) / np.maximum(k1, 1e-9)


_CACHE = {}


def _geometria(objeto_id):
    """Marco, contorno tabulado y límites del contorno de un riñón (se calcula una vez)."""
    if objeto_id not in _CACHE:
        cfg = PARAMETROS[objeto_id]
        c = np.asarray(cfg["centro"], dtype=float)
        poligono = _catmull_rom_cerrada(np.asarray(cfg["contorno"]) * cfg.get("escala", 1.0) / 100.0)
        _CACHE[objeto_id] = {"centro": c, "R": _marco(cfg["angulos"], -np.sign(c[1])),
                             "tabla": _tabla_distancia(poligono), "min": poligono.min(0), "max": poligono.max(0)}
    return _CACHE[objeto_id]


def _a_mundo(objeto_id, q):
    g = _geometria(objeto_id)
    return g["centro"] + np.asarray(q, dtype=float) @ g["R"].T


# ----------------------------------------------------------------------------
# Riñones
# ----------------------------------------------------------------------------
def _superficie_media(cfg, u, v):
    cu, cv = cfg["curvatura"]
    return -0.5 * (cu * u ** 2 + cv * v ** 2)


def sdf_rinon(objeto_id, p):
    """Riñón sin vecinos ni cavidad: lente sobre el contorno dorsal con el seno renal."""
    cfg, g = PARAMETROS[objeto_id], _geometria(objeto_id)
    q = (np.asarray(p, dtype=float) - g["centro"]) @ g["R"]
    u, v, w = q[:, 0], q[:, 1], q[:, 2]
    d2 = _consultar(g["tabla"], u, v)
    s = -d2
    t = np.clip((v - g["min"][1]) / (g["max"][1] - g["min"][1]), 0.0, 1.0)  # 0 lateral, 1 medial

    def mezcla(par):
        return par[0] + (par[1] - par[0]) * t

    td = mezcla(cfg["grosor_dorsal"]) * _perfil(s / mezcla(cfg["borde_dorsal"]))
    pv = _perfil(s / mezcla(cfg["borde_ventral"]))
    tv = mezcla(cfg["grosor_ventral"]) * pv
    if cfg["excavacion_ventral"]:
        e, u0, v0, r = cfg["excavacion_ventral"]
        tv = tv - e * pv * np.exp(-((u - u0) ** 2 + (v - v0) ** 2) / r ** 2)
    if cfg["cresta_dorsal"]:  # dos vertientes de psoas separadas por una cresta longitudinal baja
        vc, pendiente = cfg["cresta_dorsal"]
        tope = mezcla(cfg["grosor_dorsal"]) - pendiente * np.abs(v - vc)
        td = P.smin(td, tope, 0.003)
    w0 = _superficie_media(cfg, u, v)
    F = np.maximum(d2, np.maximum(w - w0 - td, w0 - w - tv))
    seno = cfg["seno"]
    su, sv, sw = seno["centro"]
    qs = np.stack([u - su, v - sv, w - (_superficie_media(cfg, su, sv) + sw)], axis=1)
    return smax(F, -_sdf_elipsoide(qs, np.asarray(seno["semiejes"])), 0.004)


def hilio(objeto_id):
    """Punto del hilio (borde medial, boca del seno renal) de cada riñón: origen del uréter."""
    cfg = PARAMETROS[objeto_id]
    u, v, w = cfg["hilio"]
    return _a_mundo(objeto_id, [u, v, _superficie_media(cfg, u, v) + w])


# ----------------------------------------------------------------------------
# Adrenales
# ----------------------------------------------------------------------------
def sdf_adrenal_base(objeto_id, p):
    """Lengüeta aplanada, curvada en su plano y afilada hacia caudal."""
    cfg = ADRENALES[objeto_id]
    c = np.asarray(cfg["centro"], dtype=float)
    R = _marco(cfg["angulos"], -np.sign(c[1]))
    q = (np.asarray(p, dtype=float) - c) @ R
    a, b, cc = q[:, 0], q[:, 1], q[:, 2]
    if cfg["curvatura"]:  # eje mayor en arco dentro del plano de la lámina (cóncavo hacia medial si > 0)
        sg, rb = np.sign(cfg["curvatura"]), 1.0 / abs(cfg["curvatura"])
        r = np.hypot(a, rb - sg * b)
        a, b = rb * np.arctan2(a, rb - sg * b), sg * (rb - r)
    L, W, H = cfg["semiejes"]
    f = np.clip(1.0 + cfg["afilado"] * np.clip(a / L, -1.0, 1.0), 0.35, None)  # más fina hacia caudal
    n = FORMA_ADRENAL["exponente_contorno"]
    m = FORMA_ADRENAL["exponente_grosor"]
    k_plano = (np.abs(a / L) ** n + np.abs(b / (W * f)) ** n) ** (1.0 / n)
    k = (k_plano ** m + np.abs(cc / (H * f)) ** m) ** (1.0 / m)
    return (k - 1.0) * H * f  # distancia exacta sobre las caras; menor en los bordes


def sdf_adrenal(objeto_id, p):
    """Adrenal amoldada al borde craneomedial de su riñón."""
    cfg = ADRENALES[objeto_id]
    F = sdf_adrenal_base(objeto_id, p)
    return smax(F, AJUSTE["holgura_adrenal"] - sdf_rinon(cfg["rinon"], p), 0.004)


# ----------------------------------------------------------------------------
# Interfaz
# ----------------------------------------------------------------------------
def _cavidad(F, p):
    return smax(F, C.restriccion_cavidad(p, "abdominal", AJUSTE["margen_cavidad"]), AJUSTE["suavidad_cavidad"])


def forma(objeto_id, p):
    """Forma base (sin vecinos), ya amoldada a la cavidad abdominal."""
    F = sdf_rinon(objeto_id, p) if objeto_id in PARAMETROS else sdf_adrenal(objeto_id, p)
    return _cavidad(F, p)


def _impronta(F, p, ctx, vecino, holgura, maximo=None, k=0.004):
    """Talla la impresión de un vecino (con holgura); `maximo` limita su profundidad."""
    imp = holgura - ctx.sdf(vecino)(p)
    if maximo is not None:
        imp = np.minimum(imp, F + maximo)
    return smax(F, imp, k)


VECINOS = {  # (vecino, holgura, profundidad máxima o None)
    "ABD_RINON_DER": [("ABD_VENA_CAVA_CAUDAL", AJUSTE["holgura_vaso"], None),
                      ("ABD_PANCREAS", AJUSTE["holgura_vecino"], AJUSTE["impronta_max"])],
    "ABD_RINON_IZQ": [("ABD_AORTA", AJUSTE["holgura_vaso"], None),
                      ("ABD_PANCREAS", AJUSTE["holgura_vecino"], AJUSTE["impronta_max"]),
                      ("ABD_BAZO", AJUSTE["holgura_vecino"], AJUSTE["impronta_max"])],
    "ABD_ADRENAL_DER": [("ABD_VENA_CAVA_CAUDAL", AJUSTE["holgura_vaso"], None),
                        ("ABD_PANCREAS", AJUSTE["holgura_vecino"], 0.004)],
    "ABD_ADRENAL_IZQ": [("ABD_AORTA", AJUSTE["holgura_vaso"], None),
                        ("ABD_A_MESENTERICA_CRANEAL", AJUSTE["holgura_vaso"], None),
                        ("ABD_PANCREAS", AJUSTE["holgura_vecino"], 0.004)],
}


def campo(objeto_id, p, ctx=None):
    """Forma con las impresiones de vasos, páncreas y bazo (el hígado talla él mismo las suyas)."""
    F = forma(objeto_id, p)
    if ctx is None:
        return F
    for vecino, holgura, maximo in VECINOS[objeto_id]:
        F = _impronta(F, p, ctx, vecino, holgura, maximo)
    return F


def _caja_rinon(objeto_id):
    cfg, g = PARAMETROS[objeto_id], _geometria(objeto_id)
    esquinas = np.array([[u, v, w] for u in (g["min"][0], g["max"][0]) for v in (g["min"][1], g["max"][1])
                         for w in (-0.06, 0.05)])
    V = _a_mundo(objeto_id, esquinas)
    return V.min(0) - 0.01, V.max(0) + 0.01


def _caja_adrenal(objeto_id):
    cfg = ADRENALES[objeto_id]
    c = np.asarray(cfg["centro"])
    r = cfg["semiejes"][0] * (1.0 + cfg["afilado"]) + 0.01
    return c - r, c + r


def _piezas():
    piezas = {}
    for oid in PARAMETROS:
        piezas[oid] = {"caja": _caja_rinon(oid), "h": 0.003, "sigma": 1.0, "recorte": "abdominal",
                       "voxel": 0.003}
    for oid in ADRENALES:
        piezas[oid] = {"caja": _caja_adrenal(oid), "h": 0.0015, "sigma": 0.8, "recorte": "abdominal",
                       "voxel": 0.0015, "radio_min": 0.006}
    return piezas


PIEZAS = _piezas()
