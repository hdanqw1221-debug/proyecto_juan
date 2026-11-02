"""Hígado del caballo: placa única amoldada a la cara abdominal del diafragma, con lóbulos e impresiones.

Especificación de la forma (Bradley 1922, fig. 56 p121; Sisson y Grossman; Dyce–Sack–Wensing;
docs/investigacion_anatomica_cavidades_equinas.md §3.7):
- ~5 kg (≈1–1,5 % del peso vivo: ~5000 cm³); casi todo en la parte intratorácica del abdomen, aplicado
  a la cara abdominal del diafragma, craneal a la última costilla y sobre todo a la derecha del plano
  medio. A la derecha llega dorsalmente hasta el riñón derecho; los lóbulos izquierdos bajan ventralmente.
- Una sola placa ancha (~50–55 cm transversal, ~40 cm de alto): cara diafragmática convexa (molde de la
  cúpula) y cara visceral cóncava, con las impresiones de las vísceras.
- Lóbulos como colgajos separados por escotaduras profundas del borde ventral: lateral izquierdo (el
  mayor, redondeado, ventral e izquierdo), medial izquierdo, cuadrado (central, ventral y pequeño:
  «lóbulo central» de Bradley, con la fisura umbilical), lateral derecho (dorsal derecho) y caudado,
  cuyo proceso caudado se proyecta dorsal y caudalmente a la derecha y lleva la impresión renal.
  Sin vesícula biliar.
- Borde dorsal grueso (5–10 cm) con el surco de la vena cava caudal (en parte incluida en el órgano) y,
  a su izquierda, la escotadura esofágica de bordes redondeados; bordes derecho e izquierdo delgados;
  borde ventral delgado (~1 cm) cortado por dos fisuras interlobulares profundas y la fisura umbilical.
- Cara visceral: impresiones gástrica, duodenal, cólica, cecal, renal y adrenal (sustracción suave de
  los vecinos, de profundidad limitada para no perforar nunca la placa) y porta hepática: depresión
  oblicua alargada, dorsal al centro del órgano e inmediatamente a la derecha del plano medio.
Construcción: coordenadas propias sobre el diafragma (pie (u, v) = (y, z) del punto más próximo de un
diafragma con el pliegue cúpula / plano base redondeado y distancia `a` según su normal, de modo que
los bordes quedan perpendiculares a la placa y la placa sigue la cúpula sin cizallarse) -> contorno 2D
(lóbulos, borde dorsal, escotadura esofágica, fisuras) -> mapa de grosor (bordes delgados; masa central,
borde dorsal, banda de la cava, proceso caudado y lóbulo derecho gruesos; porta) -> lámina ->
impresiones de los vecinos (profundidad limitada, capa mínima conservada) y límite de la cavidad.
"""
import numpy as np

from anatomia import referencia as ref
from anatomia.organos import contexto as C
from blender import primitivas as P

PARAMETROS = {
    "separacion": 0.010,        # cara diafragmática respecto al diafragma (m, según la normal)
    # Separación añadida junto al pliegue cúpula / plano base del diafragma de referencia (arista de ~65°):
    # la cara diafragmática lo rodea con un radio mayor en vez de copiar la arista (m, ancho en q).
    "separacion_pliegue": (0.012, 0.30),
    "redondeo_cupula": 0.35,    # ancho (en unidades de q) del redondeo del pliegue cúpula / plano base
    # Contorno en (y, z) del pie sobre el diafragma: lóbulos (centro, semiejes, giro en grados).
    "lobulos": {
        "lateral_izquierdo": ((0.125, 0.335), (0.095, 0.140), -14.0),
        "medial_izquierdo": ((0.012, 0.375), (0.050, 0.110), 3.0),
        "cuadrado": ((-0.055, 0.385), (0.048, 0.100), -3.0),
        "lateral_derecho": ((-0.180, 0.460), (0.090, 0.165), 4.0),
        "cuerpo": ((-0.040, 0.500), (0.170, 0.090), 0.0),        # base común dorsal (lóbulo caudado)
        "proceso_caudado": ((-0.140, 0.560), (0.060, 0.045), 0.0),
    },
    "union_lobulos": 0.014,     # radio de la unión suave de los lóbulos (m en y, z)
    # Borde dorsal: z máxima del contorno según y (de izquierda a derecha).
    "borde_dorsal": [(0.26, 0.40), (0.17, 0.475), (0.10, 0.525), (0.06, 0.545), (0.00, 0.560),
                     (-0.04, 0.568), (-0.09, 0.578), (-0.14, 0.580), (-0.20, 0.576), (-0.25, 0.565),
                     (-0.30, 0.50)],
    "redondeo_dorsal": 0.012,
    # Escotadura esofágica del borde dorsal: centro (y, z), radio del corte y redondeo de sus bordes.
    "escotadura_esofagica": ((0.025, 0.538), 0.028, 0.010),
    # Fisuras del borde ventral: hendiduras estrechas que prolongan hacia dorsal la escotadura que dejan
    # entre sí los contornos redondeados de dos lóbulos vecinos. (y, z) del fondo, (y, z) de la boca,
    # semiancho en el fondo y en la boca (m en y, z). Interlobular izquierda (lateral / medial
    # izquierdos), umbilical (medial izquierdo / cuadrado) e interlobular derecha (cuadrado / lateral derecho).
    "fisuras": [((0.040, 0.400), (0.050, 0.262), 0.0045, 0.0110),
                ((-0.020, 0.340), (-0.021, 0.258), 0.0048, 0.0085),
                ((-0.091, 0.440), (-0.101, 0.330), 0.0045, 0.0110)],
    "redondeo_fisura": 0.006,
    # Grosor (m, según la normal).
    "grosor_borde": 0.011,      # bordes ventral, derecho e izquierdo
    "grosor_lobulo": 0.025,     # meseta de los lóbulos lejos de los bordes
    "transicion_borde": 0.11,   # distancia (en y, z) del borde a la meseta: lóbulos en cuña
    # Masa central (lóbulo caudado y región de la porta): centro, semiejes y grosor añadido.
    "masa_central": ((-0.038, 0.495), (0.110, 0.080), 0.029),
    # Engrosamiento del borde dorsal según y (y, grosor añadido) y su caída hacia ventral.
    "cresta_dorsal": [(0.24, 0.0), (0.16, 0.015), (0.08, 0.025), (0.02, 0.030), (-0.04, 0.030),
                      (-0.10, 0.035), (-0.16, 0.035), (-0.22, 0.022), (-0.27, 0.008), (-0.30, 0.0)],
    "caida_cresta": 0.085,
    "proceso_caudado_grosor": ((-0.155, 0.562), 0.045, 0.034),   # centro, radio, grosor añadido
    # Banda gruesa a lo largo del surco de la vena cava (del borde dorsal al foramen): segmento, semiancho, grosor.
    "banda_cava": (((-0.047, 0.565), (-0.042, 0.405)), 0.035, 0.022),
    "lobulo_derecho_grosor": ((-0.185, 0.455), (0.070, 0.110), 0.012),
    # Porta hepática: depresión oblicua de la cara visceral (centro, dirección del eje largo, semiejes, hondura).
    "porta": ((-0.065, 0.485), (0.55, -0.83), (0.050, 0.016), 0.012),
    # Impresiones de los vecinos.
    "holgura": 0.003,
    "impronta_max": 0.028,      # hondura máxima de una impresión visceral
    "capa_minima": 0.010,       # parénquima que siempre se conserva bajo una impresión (sin perforar)
    "suavidad_impronta": 0.016,
    "holgura_vaso": 0.004,
    "holgura_esofago": 0.008,
}

# Estructuras cuya impresión talla el propio hígado (no lo tallan a él). El páncreas no: se amolda él.
IMPRESIONES = {"ABD_ESTOMAGO", "ABD_DUODENO", "ABD_COLON_DORSAL_DER", "ABD_COLON_TRANSVERSO",
               "ABD_FLEXURA_DIAFRAGMATICA", "ABD_CIEGO", "ABD_RINON_DER", "ABD_ADRENAL_DER", "ABD_VENA_CAVA_CAUDAL",
               "TOR_ESOFAGO"}
VISCERALES = ("ABD_ESTOMAGO", "ABD_DUODENO", "ABD_COLON_DORSAL_DER", "ABD_COLON_TRANSVERSO", "ABD_FLEXURA_DIAFRAGMATICA",
              "ABD_CIEGO", "ABD_RINON_DER", "ABD_ADRENAL_DER")

PIEZAS = {
    "ABD_HIGADO": {"caja": ((0.75, -0.29, 0.17), (1.355, 0.235, 0.62)), "h": 0.0045, "sigma": 0.7,
                   "recorte": "abdominal", "voxel": 0.0045, "taubin": 4},
}


def _smax(a, b, k):
    return -P.smin(-a, -b, k)


def _tabla(puntos, paso=0.002):
    """Curva suave (Catmull-Rom) tabulada como función de la primera coordenada (creciente)."""
    p = np.array([(a, b, 0.0) for a, b in puntos], dtype=float)
    c, _, _ = P.catmull_rom(p, paso=paso)
    orden = np.argsort(c[:, 0])
    return c[orden, 0], c[orden, 1]


_BORDE_DORSAL = _tabla(PARAMETROS["borde_dorsal"])
_CRESTA = _tabla(PARAMETROS["cresta_dorsal"])


# ----------------------------------------------------------------------------
# Coordenadas sobre el diafragma
# ----------------------------------------------------------------------------
def _diafragma_liso(y, z):
    """x del diafragma con el pliegue cúpula / plano base redondeado (C1) y sus derivadas primeras y segundas.

    max(0, 1 - q) se sustituye por un tramo cúbico en [0, k] que nunca lo supera: la superficie queda
    a lo sumo ~1,5 cm caudal a la del diafragma junto al pliegue, y las normales son continuas.
    Devuelve (x, x_y, x_z, x_yy, x_yz, x_zz).
    """
    k = PARAMETROS["redondeo_cupula"]
    rz = np.where(z < ref.DIAFRAGMA_Z0, ref.DIAFRAGMA_RZ_VENTRAL, ref.DIAFRAGMA_RZ_DORSAL)
    q = (y / ref.DIAFRAGMA_RY) ** 2 + ((z - ref.DIAFRAGMA_Z0) / rz) ** 2
    u = 1.0 - q
    uc = np.clip(u, 0.0, k)
    g = np.where(u >= k, u, uc * uc * (2 * k - uc) / k ** 2)
    dg = np.where(u >= k, 1.0, uc * (4 * k - 3 * uc) / k ** 2)
    d2g = np.where((u >= k) | (u <= 0.0), 0.0, (4 * k - 6 * uc) / k ** 2)
    f = ref.DIAFRAGMA_FLECHA
    qy, qz = 2.0 * y / ref.DIAFRAGMA_RY ** 2, 2.0 * (z - ref.DIAFRAGMA_Z0) / rz ** 2
    qyy, qzz = 2.0 / ref.DIAFRAGMA_RY ** 2, 2.0 / rz ** 2
    x = ref.x_base_diafragma(z) + f * g
    xy, xz = -f * dg * qy, -0.50 / 0.70 - f * dg * qz
    xyy = f * d2g * qy * qy - f * dg * qyy
    xzz = f * d2g * qz * qz - f * dg * qzz
    xyz = f * d2g * qy * qz
    return x, xy, xz, xyy, xyz, xzz


def coordenadas(p, iteraciones=10, paso_max=0.03):
    """Pie (u, v) = (y, z) sobre el diafragma liso, distancia a según su normal (> 0 caudal) y métrica.

    El pie es el punto más próximo de la superficie (Newton sobre |S(u, v) - p|², con paso limitado):
    junto al pliegue de la cúpula la proyección por punto fijo oscila y deja aletas en la malla.
    La métrica (√(1 + |∇x|²)) convierte aproximadamente distancias en (y, z) en distancias sobre la placa.
    Los puntos sin converger (lejos, en el abanico de normales del pliegue) se marcan fuera (a grande).
    """
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    u, v = y.copy(), z.copy()
    for _ in range(iteraciones):
        X, xy, xz, xyy, xyz, xzz = _diafragma_liso(u, v)
        rx = X - x
        gu, gv = rx * xy + (u - y), rx * xz + (v - z)
        huu, hvv, huv = xy * xy + 1.0 + rx * xyy, xz * xz + 1.0 + rx * xzz, xy * xz + rx * xyz
        det = huu * hvv - huv * huv
        ok = (det > 1e-6) & (huu > 0.0)
        det = np.where(ok, det, 1.0)
        du = np.where(ok, -(hvv * gu - huv * gv) / det, -gu / (xy * xy + 1.0))
        dv = np.where(ok, -(huu * gv - huv * gu) / det, -gv / (xz * xz + 1.0))
        s = np.minimum(1.0, paso_max / np.maximum(np.hypot(du, dv), 1e-12))
        u, v = u + s * du, v + s * dv
    X, xy, xz = _diafragma_liso(u, v)[:3]
    m = np.sqrt(1.0 + xy * xy + xz * xz)
    a = ((X - x) - (u - y) * xy - (v - z) * xz) / m   # (S - p) · n, n = (1, -x_y, -x_z) / m
    residuo = np.sqrt((x + a / m - X) ** 2 + (y - a * xy / m - u) ** 2 + (z - a * xz / m - v) ** 2)
    a = np.where(residuo > 0.003, np.maximum(a, 0.3), a)
    return u, v, a, m


# ----------------------------------------------------------------------------
# Contorno y grosor
# ----------------------------------------------------------------------------
def _elipse(u, v, centro, semiejes, giro):
    c, s = np.cos(np.radians(giro)), np.sin(np.radians(giro))
    du, dv = u - centro[0], v - centro[1]
    a, b = c * du + s * dv, -s * du + c * dv
    k = np.sqrt((a / semiejes[0]) ** 2 + (b / semiejes[1]) ** 2)
    return (k - 1.0) * min(semiejes)


def _segmento(u, v, a, b):
    """Distancia al segmento a-b en (y, z) y parámetro t (0 en a, 1 en b)."""
    a, b = np.asarray(a, dtype=float), np.asarray(b, dtype=float)
    ab = b - a
    t = np.clip(((u - a[0]) * ab[0] + (v - a[1]) * ab[1]) / (ab @ ab), 0.0, 1.0)
    return np.hypot(u - a[0] - t * ab[0], v - a[1] - t * ab[1]), t


def contorno(u, v, con_dorsal=True):
    """Distancia 2D con signo (< 0 dentro) al contorno de la placa en (y, z)."""
    cfg = PARAMETROS
    H = None
    for centro, semiejes, giro in cfg["lobulos"].values():
        e = _elipse(u, v, centro, semiejes, giro)
        H = e if H is None else P.smin(H, e, cfg["union_lobulos"])
    if con_dorsal:
        H = _smax(H, v - np.interp(u, *_BORDE_DORSAL), cfg["redondeo_dorsal"])
        (ce, re, ke) = cfg["escotadura_esofagica"]
        H = _smax(H, re - np.hypot(u - ce[0], v - ce[1]), ke)
    for fondo, borde, r0, r1 in cfg["fisuras"]:
        d, t = _segmento(u, v, fondo, borde)
        H = _smax(H, (r0 + (r1 - r0) * t) - d, cfg["redondeo_fisura"])
    return H


def grosor(u, v, H):
    """Grosor de la placa (m, según la normal) en el pie (u, v); H es el contorno en ese punto."""
    cfg = PARAMETROS
    tb = cfg["grosor_borde"]
    # Lóbulos: del borde delgado a la meseta (perfil convexo); incluye los bordes de las fisuras.
    s = np.clip(-H / cfg["transicion_borde"], 0.0, 1.0)
    t = tb + (cfg["grosor_lobulo"] - tb) * (1.0 - (1.0 - s) ** 2)
    # Masa central, borde dorsal grueso (cresta bajo el límite dorsal que cae hacia ventral), proceso
    # caudado y lóbulo derecho.
    (cm, sm, gm) = cfg["masa_central"]
    extra = gm * np.exp(-((u - cm[0]) / sm[0]) ** 2 - ((v - cm[1]) / sm[1]) ** 2)
    zr = np.interp(u, *_BORDE_DORSAL) - 0.025
    extra = extra + np.interp(u, *_CRESTA) * np.exp(-(np.maximum(zr - v, 0.0) / cfg["caida_cresta"]) ** 2)
    (cc, rc, gc) = cfg["proceso_caudado_grosor"]
    extra = extra + gc * np.exp(-((u - cc[0]) ** 2 + (v - cc[1]) ** 2) / rc ** 2)
    (a_, b_), sb, gb = cfg["banda_cava"]
    extra = extra + gb * np.exp(-(_segmento(u, v, a_, b_)[0] / sb) ** 2)
    (cd, sd, gd) = cfg["lobulo_derecho_grosor"]
    extra = extra + gd * np.exp(-((u - cd[0]) / sd[0]) ** 2 - ((v - cd[1]) / sd[1]) ** 2)
    # Se desvanece en los bordes ventrales, laterales y de las fisuras (solo el dorsal es grueso).
    lejos_borde = np.clip(-contorno(u, v, con_dorsal=False) / 0.06, 0.0, 1.0)
    t = t + extra * lejos_borde * lejos_borde * (3.0 - 2.0 * lejos_borde)
    # Porta hepática: depresión oblicua y alargada de la cara visceral.
    (cp, ep, sp, hp) = cfg["porta"]
    e = np.asarray(ep) / np.linalg.norm(ep)
    a = (u - cp[0]) * e[0] + (v - cp[1]) * e[1]
    b = -(u - cp[0]) * e[1] + (v - cp[1]) * e[0]
    t = t - hp * np.exp(-(a / sp[0]) ** 2 - (b / sp[1]) ** 2)
    return np.maximum(t, tb)


def _separacion_pliegue(u, v):
    """Separación añadida en torno al pliegue de la cúpula (q ≈ 1, ensanchada hacia la cúpula) en el pie (u, v)."""
    extra, ancho = PARAMETROS["separacion_pliegue"]
    rz = np.where(v < ref.DIAFRAGMA_Z0, ref.DIAFRAGMA_RZ_VENTRAL, ref.DIAFRAGMA_RZ_DORSAL)
    q = (u / ref.DIAFRAGMA_RY) ** 2 + ((v - ref.DIAFRAGMA_Z0) / rz) ** 2
    return extra * np.exp(-((q - 0.92) / ancho) ** 2)


def _placa(p):
    """Campo de la placa sin vecinos y las coordenadas usadas (para las impresiones)."""
    cfg = PARAMETROS
    u, v, a, m = coordenadas(p)
    H = contorno(u, v)
    t = grosor(u, v, H)
    sep = cfg["separacion"] + _separacion_pliegue(u, v)
    lamina = np.maximum(sep - a, a - sep - t)
    F = _smax(lamina, H * m, 0.010)
    F = _smax(F, C.restriccion_cavidad(p, "abdominal", 0.008), 0.010)
    return F, a - sep, sep + t - a


def forma(objeto_id, p):
    return _placa(np.asarray(p, dtype=float))[0]


def campo(objeto_id, p, ctx):
    """Placa con las impresiones viscerales, el surco de la vena cava y la escotadura esofágica."""
    cfg = PARAMETROS
    p = np.asarray(p, dtype=float)
    F, desde_frente, desde_dorso = _placa(p)
    # Las impresiones solo suben el campo: basta evaluarlas cerca de la superficie.
    cerca = F < 0.015
    q, Fc = p[cerca], F[cerca]
    frente, dorso = desde_frente[cerca], desde_dorso[cerca]
    k = cfg["suavidad_impronta"]
    for vecino in VISCERALES:  # desde la cara visceral: nunca pasan de la capa diafragmática mínima
        imp = np.minimum(cfg["holgura"] - ctx.sdf(vecino)(q), Fc + cfg["impronta_max"])
        Fc = _smax(Fc, np.minimum(imp, frente - cfg["capa_minima"]), k)
    # Vena cava caudal: surco de la cara diafragmática y del borde dorsal (conserva la capa visceral).
    imp = cfg["holgura_vaso"] - C.campo_tubos(ctx.curvas("ABD_VENA_CAVA_CAUDAL"))(q)
    Fc = _smax(Fc, np.minimum(imp, dorso - cfg["capa_minima"]), 0.006)
    if ctx.esofago_final:  # esófago abdominal camino del cardias
        imp = cfg["holgura_esofago"] - C.campo_tubos(ctx.esofago_final)(q)
        Fc = _smax(Fc, imp, 0.008)
    F = F.copy()
    F[cerca] = Fc
    return F
