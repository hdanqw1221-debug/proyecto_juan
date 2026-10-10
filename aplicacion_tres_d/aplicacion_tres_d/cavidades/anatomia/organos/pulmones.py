"""Pulmones izquierdo y derecho: ocupan la cavidad pleural menos el mediastino."""
import numpy as np

from anatomia import especificacion as E
from anatomia import referencia as ref
from anatomia.organos import contexto as C
from blender import primitivas as P

PARAMETROS = {
    "x_max": 1.58,
    "mediastino": 0.035,
    "dilatacion_corazon": 0.015,
    # Escotadura cardíaca izquierda: 3.ª–6.ª costilla, ventral.
    "escotadura_izq": {"min": (float(ref.x_costilla(6)), 0.0, -0.10), "max": (float(ref.x_costilla(3)), 0.40, 0.22)},
    # Lóbulo accesorio del pulmón derecho (receso mediastínico, junto a la vena cava caudal).
    "lobulo_accesorio": {"min": (0.0, -0.04, 0.24), "max": (1.34, 0.03, 0.48)},
    "dilatacion_tubos": 0.010,
}

# Tubos mediastínicos que el pulmón rodea.
TUBOS_MEDIASTINO = ("TOR_TRAQUEA", "TOR_ESOFAGO", "TOR_AORTA", "TOR_VENA_CAVA_CRANEAL", "TOR_VENA_CAVA_CAUDAL")

PIEZAS = {
    "TOR_PULMON_DER": {"caja": ((0.74, -0.33, -0.03), (1.60, 0.05, 0.72)), "h": 0.01, "sigma": 1.0,
                       "recorte": "toracica"},
    "TOR_PULMON_IZQ": {"caja": ((0.74, 0.0, -0.03), (1.60, 0.33, 0.72)), "h": 0.01, "sigma": 1.0,
                       "recorte": "toracica"},
}


def campo(objeto_id, p, ctx):
    cfg = PARAMETROS
    izquierdo = objeto_id == "TOR_PULMON_IZQ"
    s = 1.0 if izquierdo else -1.0
    corazon = ctx.sdf("TOR_CORAZON")
    # Los tubos del mediastino se toman de la especificación (el esófago torácico, sin prolongar).
    tubos = []
    for oid in TUBOS_MEDIASTINO:
        tubos += C.curvas_de_spec(E.TUBOS[oid])
    sdf_med = C.campo_tubos(tubos)
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    lim = np.maximum(ref.x_diafragma(y, z) + 0.012, ref.x_borde_basal_pulmon(z))
    c = np.maximum(ref.distancia_envolvente(x, y, z) + 0.008, x - cfg["x_max"])
    c = np.maximum(c, lim - x)
    med = cfg["mediastino"] - s * y
    if not izquierdo:
        acc = cfg["lobulo_accesorio"]
        med = np.minimum(med, P.sdf_caja(p, acc["min"], acc["max"]))
    c = np.maximum(c, med)
    c = np.maximum(c, cfg["dilatacion_corazon"] - corazon(p))
    if izquierdo:
        esc = cfg["escotadura_izq"]
        c = np.maximum(c, -P.sdf_caja(p, esc["min"], esc["max"]))
    c = np.maximum(c, cfg["dilatacion_tubos"] - sdf_med(p))
    return c


def forma(objeto_id, p):
    return campo(objeto_id, p, C.Contexto())
