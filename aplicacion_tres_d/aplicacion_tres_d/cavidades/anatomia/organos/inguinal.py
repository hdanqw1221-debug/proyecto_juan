"""Testículos y epidídimos del semental (región inguinal y escroto).

Especificación de la forma (Sisson y Grossman; Dyce–Sack–Wensing; Budras, Anatomy of the Horse;
docs/investigacion_anatomica_cavidades_equinas.md §5):
- Testículo ovoide, ~12 × 6,5 × 8 cm, algo comprimido lateralmente, con el eje mayor casi horizontal
  (craneocaudal) en el semental y el polo craneal algo más ancho que el caudal.
- Epidídimo adosado al borde dorsolateral del testículo: cabeza ancha que cubre el polo craneal,
  cuerpo estrecho y aplanado a lo largo del borde dorsal y cola voluminosa que sobresale del polo
  caudal (de ella sale el conducto deferente). Amoldado a la superficie del testículo.
"""
import numpy as np

from anatomia import especificacion as E
from blender import primitivas as P

PARAMETROS = {
    "compresion_lateral": 0.82,   # semieje transversal / semieje dorsoventral
    "polo_craneal": 0.10,         # ensanchamiento del polo craneal respecto al caudal
    # Epidídimo: posición a lo largo del eje mayor (-1 caudal, +1 craneal), acimut sobre la superficie
    # (0 dorsal, positivo hacia lateral, en grados) y radio de la sección en cada punto.
    "epididimo": [(0.93, 8.0, 0.016), (0.72, 30.0, 0.015), (0.42, 42.0, 0.009), (0.05, 46.0, 0.0065),
                  (-0.35, 44.0, 0.0065), (-0.70, 36.0, 0.010), (-0.93, 18.0, 0.0165), (-1.03, 6.0, 0.015)],
    "holgura": 0.0015,            # separación epidídimo–testículo
}


def _piezas():
    piezas = {}
    for lado in ("IZQ", "DER"):
        t = E.inguinal(lado)["testiculo"]
        c = np.asarray(t["centro"])
        piezas[f"ING_TESTICULO_{lado}"] = {"caja": (c - 0.075, c + 0.075), "h": 0.0025, "sigma": 0.8,
                                           "recorte": None, "voxel": 0.0025}
        piezas[f"ING_EPIDIDIMO_{lado}"] = {"caja": (c - 0.095, c + 0.095), "h": 0.002, "sigma": 0.7,
                                           "recorte": None, "voxel": 0.002, "radio_min": 0.006}
    return piezas


PIEZAS = _piezas()


def _lado(objeto_id):
    return objeto_id.rsplit("_", 1)[1]


def _marco(lado):
    t = E.inguinal(lado)["testiculo"]
    ang = np.radians(t["inclinacion_grados"])
    rot = np.array([[np.cos(ang), 0, -np.sin(ang)], [0, 1, 0], [np.sin(ang), 0, np.cos(ang)]])
    return np.asarray(t["centro"], dtype=float), np.asarray(t["semiejes"], dtype=float), rot


def sdf_testiculo(lado, p):
    """Ovoide comprimido lateralmente y algo más ancho en el polo craneal."""
    c, a, rot = _marco(lado)
    q = (np.asarray(p) - c) @ rot  # coordenadas locales (eje mayor, transversal, dorsoventral)
    u = np.clip(q[:, 0] / a[0], -1.0, 1.0)
    ensancha = 1.0 + PARAMETROS["polo_craneal"] * u
    sy = a[2] * PARAMETROS["compresion_lateral"] * ensancha
    sz = a[2] * ensancha
    k = np.sqrt((q[:, 0] / a[0]) ** 2 + (q[:, 1] / sy) ** 2 + (q[:, 2] / sz) ** 2)
    return (k - 1.0) * np.minimum(sy, a[0])


def _curva_epididimo(lado):
    """Línea media del epidídimo sobre la superficie dorsolateral del testículo (mundo)."""
    c, a, rot = _marco(lado)
    s = 1.0 if lado == "IZQ" else -1.0
    puntos, radios = [], []
    for u, acimut, r in PARAMETROS["epididimo"]:
        ens = 1.0 + PARAMETROS["polo_craneal"] * np.clip(u, -1, 1)
        sy, sz = a[2] * PARAMETROS["compresion_lateral"] * ens, a[2] * ens
        ang = np.radians(acimut)
        # Punto de la superficie a esa altura del eje (más allá de los polos, sobre la prolongación).
        rad = np.sqrt(max(1.0 - min(abs(u), 1.0) ** 2, 0.0))
        local = np.array([u * a[0], s * sy * rad * np.sin(ang), sz * rad * np.cos(ang)])
        n = np.array([u / a[0], s * rad * np.sin(ang) / sy, rad * np.cos(ang) / sz])
        n /= np.linalg.norm(n)
        puntos.append(c + rot @ (local + (r * 0.8) * n))
        radios.append(r)
    curva, sarc, sc = P.catmull_rom(np.array(puntos), paso=0.002)
    return curva, P.perfil_radios(radios, sarc, sc)


_CURVAS = {lado: _curva_epididimo(lado) for lado in ("IZQ", "DER")}


def forma(objeto_id, p):
    lado = _lado(objeto_id)
    if objeto_id.startswith("ING_TESTICULO"):
        return sdf_testiculo(lado, p)
    curva, radios = _CURVAS[lado]
    return P.sdf_capsulas(p, curva, radios)


def campo(objeto_id, p, ctx=None):
    F = forma(objeto_id, p)
    if objeto_id.startswith("ING_EPIDIDIMO"):
        # Amoldado a la superficie del testículo.
        F = -P.smin(-F, -(PARAMETROS["holgura"] - sdf_testiculo(_lado(objeto_id), p)), 0.004)
    return F
