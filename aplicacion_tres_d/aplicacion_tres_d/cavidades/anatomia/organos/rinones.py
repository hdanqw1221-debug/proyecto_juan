"""Riñones (derecho cordiforme, izquierdo reniforme) y glándulas adrenales."""
import numpy as np

from blender import primitivas as P

RINONES = {
    "ABD_RINON_DER": {"centro": (0.80, -0.10, 0.60), "semiejes": (0.08, 0.075, 0.03), "forma": "cordiforme"},
    "ABD_RINON_IZQ": {"centro": (0.66, 0.10, 0.60), "semiejes": (0.09, 0.06, 0.03), "forma": "reniforme"},
}
ADRENALES = {
    "ABD_ADRENAL_DER": {"centro": (0.86, -0.05, 0.58), "semiejes": (0.04, 0.015, 0.01)},
    "ABD_ADRENAL_IZQ": {"centro": (0.74, 0.06, 0.58), "semiejes": (0.04, 0.015, 0.01)},
}


def _piezas():
    piezas = {}
    for oid, r in RINONES.items():
        c = np.array(r["centro"])
        piezas[oid] = {"caja": (c - 0.11, c + 0.11), "h": 0.004, "sigma": 1.0, "recorte": "abdominal"}
    for oid, r in ADRENALES.items():
        c, a = np.array(r["centro"]), np.array(r["semiejes"])
        piezas[oid] = {"caja": (c - a - 0.01, c + a + 0.01), "h": 0.0015, "sigma": 0.8, "recorte": "abdominal",
                       "radio_min": 0.006}
    return piezas


PIEZAS = _piezas()


def sdf_rinon(objeto_id, p):
    r = RINONES[objeto_id]
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


def sdf_adrenal(objeto_id, p):
    r = ADRENALES[objeto_id]
    return P.sdf_elipsoide(p, r["centro"], r["semiejes"])


def forma(objeto_id, p):
    return sdf_rinon(objeto_id, p) if objeto_id in RINONES else sdf_adrenal(objeto_id, p)


def campo(objeto_id, p, ctx=None):
    return forma(objeto_id, p)


def hilio(objeto_id):
    """Punto del hilio (borde medial) de cada riñón, origen del uréter."""
    r = RINONES[objeto_id]
    c = np.array(r["centro"])
    lado = 1.0 if c[1] > 0 else -1.0
    return c + np.array([0.0, -lado * r["semiejes"][1] * 0.75, -0.005])
