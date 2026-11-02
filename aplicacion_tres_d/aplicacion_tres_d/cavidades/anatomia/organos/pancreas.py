"""Páncreas del caballo: glándula triangular aplanada en el techo del abdomen craneal.

Especificación de la forma (Bradley 1922, p. 118–119; Sisson y Grossman; Dyce–Sack–Wensing;
docs/investigacion_anatomica_cavidades_equinas.md §3.9):
- ~350 g; triangular, aplanado dorsoventralmente, con la superficie lobulillada.
- Cuerpo en la flexura craneal del duodeno, a la derecha del plano medio y ventral a la vena cava
  caudal, atravesado por el anillo pancreático (anulus pancreatis), el orificio por el que pasa la vena
  porta, propio del caballo (es el único agujero de la malla: género 1).
- Lóbulo derecho: se extiende caudalmente a lo largo de la cara medial del duodeno descendente, hacia
  el riñón derecho y la base del ciego; el duodeno queda alojado en su borde (impresión duodenal).
- Lóbulo izquierdo: hacia la izquierda, caudal al saco ciego del estómago y dorsal a él, hasta el
  bazo y el polo craneal del riñón izquierdo.
- Cara dorsal: vena cava caudal, aorta, adrenales y riñones; cara ventral: colon transverso,
  colon dorsal derecho y estómago.
"""
import numpy as np

from anatomia.organos import contexto as C
from blender import primitivas as P

PARAMETROS = {
    # Brazos de la glándula desde el cuerpo: puntos de la línea media, semiancho (en el plano de la
    # lámina) y semigrosor (dorsoventral).
    "cuerpo": {"puntos": [(0.872, -0.040, 0.521), (0.850, -0.020, 0.524), (0.826, 0.004, 0.530)],
               "semiancho": [0.034, 0.038, 0.032], "semigrosor": [0.013, 0.014, 0.013]},
    "lobulo_derecho": {"puntos": [(0.845, -0.032, 0.524), (0.805, -0.050, 0.531), (0.768, -0.058, 0.535)],
                       "semiancho": [0.026, 0.020, 0.012], "semigrosor": [0.012, 0.010, 0.007]},
    "lobulo_izquierdo": {"puntos": [(0.828, 0.000, 0.530), (0.810, 0.050, 0.549), (0.795, 0.098, 0.563),
                                    (0.785, 0.135, 0.571)],
                         "semiancho": [0.032, 0.026, 0.019, 0.011], "semigrosor": [0.014, 0.012, 0.009, 0.006]},
    "redondeo": 0.030,          # unión suave de los brazos (forma triangular)
    # Anillo pancreático: paso de la vena porta a través del cuerpo (centro, eje y radio del orificio).
    "anillo_porta": {"centro": (0.818, -0.018, 0.526), "eje": (0.0, 0.25, 1.0), "radio": 0.012},
    "lobulillos": {"amplitud": 0.0015, "periodo": 0.022},   # relieve lobulillar de la superficie
    "holgura": 0.003,           # separación con vecinos tallados
    "impronta_max": 0.012,      # profundidad máxima de las impresiones
}

# Estructuras cuya impresión talla el propio páncreas (no lo tallan a él).
IMPRESIONES = {"ABD_DUODENO"}

PIEZAS = {
    "ABD_PANCREAS": {"caja": ((0.68, -0.17, 0.48), (0.93, 0.17, 0.62)), "h": 0.004, "sigma": 0.8,
                     "recorte": "abdominal", "voxel": 0.004},
}


def _smax(a, b, k):
    return -P.smin(-a, -b, k)


def _brazo(p, cfg):
    """Banda aplanada de sección elíptica (semiancho en el plano xy, semigrosor en z) a lo largo de puntos."""
    c, s, sc = P.catmull_rom(cfg["puntos"], paso=0.004)
    a = P.perfil_radios(cfg["semiancho"], s, sc)
    b = P.perfil_radios(cfg["semigrosor"], s, sc)
    salida = np.full(len(p), np.inf)
    for i in range(len(c) - 1):
        a0, ab = c[i], c[i + 1] - c[i]
        t = np.clip(((p - a0) @ ab) / max(ab @ ab, 1e-12), 0.0, 1.0)
        v = p - (a0 + t[:, None] * ab)
        ai = a[i] + t * (a[i + 1] - a[i])
        bi = b[i] + t * (b[i + 1] - b[i])
        horiz = np.sqrt(v[:, 0] ** 2 + v[:, 1] ** 2)
        d = (np.sqrt((horiz / ai) ** 2 + (v[:, 2] / bi) ** 2) - 1.0) * np.minimum(ai, bi)
        salida = np.minimum(salida, d)
    return salida


def _lobulillos(p):
    """Relieve lobulillar: suma de ondas en tres direcciones fijas (determinista)."""
    lb = PARAMETROS["lobulillos"]
    k = 2 * np.pi / lb["periodo"]
    dirs = np.array([[0.8, 0.5, 0.33], [-0.4, 0.85, 0.35], [0.3, -0.35, 0.89]])
    dirs /= np.linalg.norm(dirs, axis=1, keepdims=True)
    return lb["amplitud"] * np.prod(np.sin(k * (p @ dirs.T) + np.array([0.3, 1.7, 2.9])), axis=1) * 2.5


def forma(objeto_id, p):
    cfg = PARAMETROS
    F = _brazo(p, cfg["cuerpo"])
    for clave in ("lobulo_derecho", "lobulo_izquierdo"):
        F = P.smin(F, _brazo(p, cfg[clave]), cfg["redondeo"])
    esc = cfg["anillo_porta"]
    eje = np.asarray(esc["eje"], dtype=float)
    eje /= np.linalg.norm(eje)
    v = p - np.asarray(esc["centro"])
    radial = np.linalg.norm(v - (v @ eje)[:, None] * eje, axis=1)
    F = _smax(F, esc["radio"] - radial, 0.008)
    return F + _lobulillos(p)


def campo(objeto_id, p, ctx):
    cfg = PARAMETROS
    F = forma(objeto_id, p)
    tope = F + cfg["impronta_max"]
    # Impresión duodenal y del hígado (el páncreas se amolda a ambos).
    for vecino in ("ABD_DUODENO", "ABD_HIGADO", "ABD_VENA_CAVA_CAUDAL"):
        impronta = np.minimum(cfg["holgura"] - ctx.sdf(vecino)(p), tope)
        F = _smax(F, impronta, 0.008)
    return _smax(F, C.restriccion_cavidad(p, "abdominal"), 0.008)
