"""Páncreas: glándula alargada y aplanada en el techo del abdomen craneal, caudal al hígado."""
import numpy as np

from anatomia.organos import contexto as C
from blender import primitivas as P

PARAMETROS = {"puntos": [(0.86, -0.12, 0.53), (0.88, -0.03, 0.52), (0.80, 0.08, 0.56)], "radios": [0.05, 0.035, 0.02],
              "aplanamiento_z": 0.6}

PIEZAS = {
    "ABD_PANCREAS": {"caja": ((0.72, -0.20, 0.44), (0.96, 0.13, 0.63)), "h": 0.005, "sigma": 1.0,
                     "recorte": "abdominal"},
}


def sdf_pancreas(p):
    cfg = PARAMETROS
    c, s, sc = P.catmull_rom(cfg["puntos"], paso=0.01)
    r = P.perfil_radios(cfg["radios"], s, sc)
    centro_z = np.mean(np.array(cfg["puntos"])[:, 2])
    q = p.copy()
    q[:, 2] = centro_z + (p[:, 2] - centro_z) / cfg["aplanamiento_z"]
    c2 = c.copy()
    c2[:, 2] = centro_z + (c[:, 2] - centro_z) / cfg["aplanamiento_z"]
    return P.sdf_capsulas(q, c2, r) * cfg["aplanamiento_z"]


def forma(objeto_id, p):
    return sdf_pancreas(p)


def campo(objeto_id, p, ctx):
    F = np.maximum(sdf_pancreas(p), C.restriccion_cavidad(p, "abdominal"))
    return np.maximum(F, 0.004 - ctx.sdf("ABD_HIGADO")(p))
