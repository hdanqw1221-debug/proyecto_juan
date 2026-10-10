"""Estómago.

Su línea media forma parte de la cadena digestiva que se relaja para separar los tubos
(especificacion.CADENAS). Con USAR_CAMPO = False se malla como tubo sobre esa línea; con
USAR_CAMPO = True se malla con `campo`, construido alrededor de la línea relajada
(ctx.lineas["ABD_ESTOMAGO"] = (curva, radios)).
"""
import numpy as np

from anatomia.organos import contexto as C
from blender import primitivas as P

USAR_CAMPO = False
FORMA_CON_CONTEXTO = True  # la forma depende de la línea media relajada


def piezas(ctx):
    curva, radios = ctx.lineas["ABD_ESTOMAGO"]
    margen = float(np.max(radios)) * 1.6 + 0.02
    return {"ABD_ESTOMAGO": {"caja": (curva.min(0) - margen, curva.max(0) + margen), "h": 0.006, "sigma": 1.0,
                             "recorte": "abdominal"}}


def forma(objeto_id, p, ctx):
    curva, radios = ctx.lineas["ABD_ESTOMAGO"]
    return P.sdf_capsulas(p, curva, radios)


def campo(objeto_id, p, ctx):
    return np.maximum(forma(objeto_id, p, ctx), C.restriccion_cavidad(p, "abdominal"))
