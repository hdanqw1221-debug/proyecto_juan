"""Bazo del caballo: lámina falciforme aplicada a la pared abdominal izquierda.

Especificación de la forma (Bradley 1922, fig. 55 p119; Sisson y Grossman; Dyce–Sack–Wensing;
docs/investigacion_anatomica_cavidades_equinas.md §3.8):
- Contorno triangular (falciforme), ~50 cm de largo, ~1–1,5 kg: base (extremidad dorsal) ancha y
  truncada bajo las últimas costillas, casi hasta la columna, entre el diafragma y el riñón izquierdo;
  ápice (extremidad ventral) romo, dirigido ventral y cranealmente hasta el 9.º–10.º espacio intercostal.
- Cara parietal convexa, aplicada al diafragma y a la pared costal izquierda; cara visceral cóncava.
- Hilio: surco longitudinal de la cara visceral, cerca del borde craneal, con los vasos esplénicos;
  divide la cara gástrica (craneal, estrecha, amoldada a la curvatura mayor del estómago) de la
  intestinal (caudal, amplia).
- Borde craneal cóncavo y más grueso; borde caudal convexo y delgado.
Construcción: eje sobre la pared izquierda (de la base al ápice) -> coordenadas locales (s a lo largo
del eje, v a través, d profundidad bajo la pared) -> lámina de grosor variable con surco hiliar ->
impresión gástrica y límite de la cavidad.
"""
import numpy as np

from anatomia import referencia as ref
from anatomia.organos import contexto as C
from blender import primitivas as P

PARAMETROS = {
    # Eje en la pared izquierda, de la base (dorsocaudal) al ápice (ventrocraneal): (x, z) de cada punto.
    # Calculado para que el borde craneal quede ~1,5 cm caudal al diafragma a lo largo de todo el órgano.
    "eje_xz": [(0.694, 0.625), (0.744, 0.555), (0.820, 0.465), (0.905, 0.370), (0.985, 0.285), (1.060, 0.215)],
    "semiancho": (0.095, 0.018),     # en la base y en el ápice (m)
    "exponente_ancho": 0.85,         # cómo se estrecha hacia el ápice
    "grosor": (0.036, 0.016),        # grosor máximo en la base y en el ápice (m)
    "grosor_borde": 0.007,           # grosor mínimo de los bordes (> paso de la malla: sin agujeros)
    "asimetria": 0.40,               # el grosor máximo se desplaza hacia el borde craneal
    "margen_pared": 0.010,           # separación de la cara parietal respecto a la pared (m)
    "hilio": {"v": -0.35, "ancho": 0.013, "profundidad": 0.006},  # surco hiliar (v relativo, m)
    "redondeo": 0.008,               # redondeo de bordes y extremos
    "holgura": 0.003,                # separación con el estómago (m)
    "impronta_max": 0.012,           # profundidad máxima de la impresión gástrica (m)
}

# El bazo talla en sí mismo la impresión del estómago (cara gástrica): el estómago no lo talla a él.
IMPRESIONES = {"ABD_ESTOMAGO"}

PIEZAS = {
    "ABD_BAZO": {"caja": ((0.60, 0.08, 0.12), (1.16, 0.33, 0.72)), "h": 0.005, "sigma": 0.8, "recorte": "abdominal",
                 "voxel": 0.005},
}


def _smax(a, b, k):
    return -P.smin(-a, -b, k)


def _pared_izquierda(x, z, holgura=0.0):
    """Punto de la pared izquierda de la envolvente en (x, z) y su normal hacia fuera."""
    e = ref.EXPONENTE_SECCION
    w, _, _ = ref.envolvente(x)
    zc, hz = ref.centro_y_semialtura(x)
    u = np.clip((z - zc) / hz, -0.98, 0.98)
    y = w * (1 - np.abs(u) ** e) ** (1 / e)
    n = np.array([0.0, e * y ** (e - 1) / w ** e, e * np.abs(u) ** (e - 1) / hz * np.sign(u)])
    n /= np.linalg.norm(n)
    return np.array([x, y, z]) - holgura * n, n


def _eje():
    """Puntos del eje sobre la pared (densos), arco, tangentes, normales de la pared y transversales."""
    xz = np.array(PARAMETROS["eje_xz"])
    pts3 = np.array([_pared_izquierda(x, z)[0] for x, z in xz])
    c, s, _ = P.catmull_rom(pts3, paso=0.008)
    n = np.array([_pared_izquierda(q[0], q[2])[1] for q in c])
    t = np.gradient(c, axis=0)
    t /= np.linalg.norm(t, axis=1, keepdims=True)
    tr = np.cross(n, t)  # en el plano de la pared: v > 0 hacia el borde caudal (caudoventral)
    tr /= np.linalg.norm(tr, axis=1, keepdims=True)
    return c, s, t, n, tr


_EJE = _eje()


def forma(objeto_id, p):
    cfg = PARAMETROS
    c, s_eje, t, n, tr = _EJE
    L = s_eje[-1]
    p = np.asarray(p, dtype=float)
    # Punto del eje más próximo (proyección sobre la polilínea).
    a_, ab = c[:-1], c[1:] - c[:-1]
    l2 = np.maximum(np.sum(ab * ab, axis=1), 1e-12)
    F = np.empty(len(p))
    for i in range(0, len(p), 8000):
        q = p[i:i + 8000, None, :]
        tt = np.clip(np.sum((q - a_) * ab, axis=2) / l2, 0.0, 1.0)
        dist = np.linalg.norm(q - (a_ + tt[..., None] * ab), axis=2)
        k = np.argmin(dist, axis=1)
        filas = np.arange(len(k))
        tk = tt[filas, k]
        base = a_[k] + tk[:, None] * ab[k]
        off = p[i:i + 8000] - base
        s = s_eje[k] + tk * np.sqrt(l2[k])
        # Coordenada a lo largo del eje sin recortar en los extremos (para la base truncada y el ápice).
        s = np.where(k == 0, s + np.minimum(np.sum(off * t[0], axis=1), 0.0), s)
        s = np.where(k == len(ab) - 1, s + np.maximum(np.sum(off * t[-1], axis=1), 0.0), s)
        f = np.clip(s / L, 0.0, 1.0)
        w = cfg["semiancho"][1] + (cfg["semiancho"][0] - cfg["semiancho"][1]) * (1 - f) ** cfg["exponente_ancho"]
        # Dirección transversal interpolada entre los extremos del tramo (sin escalones en los bordes).
        trk = (1 - tk)[:, None] * tr[k] + tk[:, None] * tr[np.minimum(k + 1, len(tr) - 1)]
        trk /= np.linalg.norm(trk, axis=1, keepdims=True)
        v = np.sum(off * trk, axis=1)
        vr = np.clip(v / w, -1.0, 1.0)
        g = cfg["grosor"][1] + (cfg["grosor"][0] - cfg["grosor"][1]) * (1 - f)
        perfil = np.sqrt(np.maximum(1 - vr ** 2, 0.0)) ** 0.6 * (1 - cfg["asimetria"] * vr) / (1 + cfg["asimetria"])
        hil = cfg["hilio"]
        surco = hil["profundidad"] * np.exp(-(((vr - hil["v"]) * w) / hil["ancho"]) ** 2) * np.clip((f - 0.06) / 0.1, 0, 1)
        grosor = np.maximum(g * perfil - surco, cfg["grosor_borde"])
        x, y, z = p[i:i + 8000, 0], p[i:i + 8000, 1], p[i:i + 8000, 2]
        prof = -ref.distancia_envolvente(np.clip(x, ref.X_MIN_ENVOLVENTE, None), y, z)  # > 0 dentro
        lamina = np.maximum(cfg["margen_pared"] - prof, prof - cfg["margen_pared"] - grosor)
        contorno = np.maximum(np.abs(v) - w, np.maximum(-s, s - L))
        F[i:i + 8000] = _smax(lamina, contorno, cfg["redondeo"])
    return np.maximum(F, C.restriccion_cavidad(p, "abdominal", 0.01))


def campo(objeto_id, p, ctx=None):
    cfg = PARAMETROS
    F = forma(objeto_id, p)
    if ctx is not None and "ABD_ESTOMAGO" in ctx.lineas:
        impronta = np.minimum(cfg["holgura"] - ctx.sdf("ABD_ESTOMAGO")(p), F + cfg["impronta_max"])
        F = _smax(F, impronta, 0.008)
    return F
