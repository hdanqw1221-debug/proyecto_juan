"""Vejiga urinaria (transición pélvica)."""
import numpy as np

from blender import primitivas as P

PARAMETROS = {"centro": (0.12, 0.0, 0.28), "semiejes": (0.10, 0.07, 0.07)}

_c, _a = np.array(PARAMETROS["centro"]), np.array(PARAMETROS["semiejes"])
PIEZAS = {"PEL_VEJIGA": {"caja": (_c - _a - 0.01, _c + _a + 0.01), "h": 0.005, "sigma": 0.8, "recorte": None}}


def forma(objeto_id, p):
    return P.sdf_elipsoide(p, PARAMETROS["centro"], PARAMETROS["semiejes"])


def campo(objeto_id, p, ctx=None):
    return forma(objeto_id, p)
