"""Bazo: lámina falciforme adosada a la pared abdominal izquierda."""
import numpy as np

from anatomia import referencia as ref
from anatomia.organos import contexto as C
from blender import primitivas as P

# "ancho": semiancho en la base y en el extremo ventral (base ≈ 20 cm de ancho).
PARAMETROS = {"base": (0.72, 0.27, 0.62), "extremo": (1.05, 0.26, 0.25), "ancho": (0.12, 0.025), "semigrosor": 0.016}

PIEZAS = {
    "ABD_BAZO": {"caja": ((0.60, 0.08, 0.12), (1.14, 0.33, 0.72)), "h": 0.006, "sigma": 1.0, "recorte": "abdominal"},
}


def sdf_bazo(p):
    cfg = PARAMETROS
    base, ext = np.array(cfg["base"]), np.array(cfg["extremo"])
    medio = (base + ext) / 2 + np.array([-0.03, 0.0, 0.0])
    c, s, _ = P.catmull_rom([base, medio, ext], paso=0.02)
    F = np.full(len(p), 1.0)
    e = ref.EXPONENTE_SECCION
    for i, (q, si) in enumerate(zip(c, s)):
        x, z = q[0], q[2]
        w, _, _ = ref.envolvente(x)
        zc, hz = ref.centro_y_semialtura(x)
        u = min(abs((z - zc) / hz), 0.98)
        y_pared = w * (1 - u ** e) ** (1 / e)
        n = np.array([0.0, e * y_pared ** (e - 1) / w ** e, e * u ** (e - 1) / hz * np.sign(z - zc)])
        n /= np.linalg.norm(n)
        # Centro del elemento: sobre la pared izquierda, retirado según su normal.
        centro = np.array([x, y_pared, z]) - (0.012 + cfg["semigrosor"]) * n
        tang = c[min(i + 1, len(c) - 1)] - c[max(i - 1, 0)]
        R = C.marco(tang, n)
        f = si / s[-1]
        ancho = cfg["ancho"][0] + (cfg["ancho"][1] - cfg["ancho"][0]) * f
        F = np.minimum(F, P.sdf_elipsoide(p, centro, (0.03, ancho, cfg["semigrosor"]), R))
    return np.maximum(F, C.restriccion_cavidad(p, "abdominal", 0.01))


def forma(objeto_id, p):
    return sdf_bazo(p)


def campo(objeto_id, p, ctx=None):
    return sdf_bazo(p)
