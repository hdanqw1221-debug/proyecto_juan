"""Vejiga urinaria (transición pélvica), moderadamente llena.

Especificación de la forma (Sisson y Grossman; Dyce–Sack–Wensing; Budras, Anatomy of the Horse;
docs/investigacion_anatomica_cavidades_equinas.md §4):
- Piriforme, ~19 cm desde el vértice hasta el cuello y ~13 cm de diámetro máximo (~2 L): el vértice
  (craneal) redondeado sobresale en el abdomen sobre el suelo, por delante del borde del pubis; el
  cuerpo descansa en el suelo de la pelvis, algo aplanado dorsoventralmente; el cuello (caudal) se
  estrecha en embudo y se continúa con un muñón de uretra hacia caudal.
- Los uréteres llegan por la cara dorsal, cerca del cuello (ABD_URETER_* terminan en (0,12; ±0,03; 0,30):
  unión anatómica). Dorsal a ella quedan las ampollas de los conductos deferentes y el recto.
- Contenida en la envolvente de la pelvis (el suelo sube hacia caudal: z ~0,20 a x 0,22 y ~0,27 a x 0,05).
"""
import numpy as np

from anatomia import referencia as ref
from blender import primitivas as P

PARAMETROS = {
    # Eje desde el vértice (craneal) hasta la uretra (caudal): puntos y radios (perfil de pera).
    "eje": [(0.240, 0.0, 0.272), (0.215, 0.0, 0.276), (0.180, 0.0, 0.283), (0.140, 0.0, 0.291),
            (0.100, 0.0, 0.299), (0.072, 0.0, 0.305), (0.052, 0.0, 0.309), (0.030, 0.0, 0.310),
            (0.000, 0.0, 0.306)],
    "radios": [0.028, 0.052, 0.064, 0.064, 0.055, 0.038, 0.020, 0.013, 0.011],
    "aplanamiento": 0.86,     # semieje dorsoventral / transversal del cuerpo
    "margen_pelvis": 0.008,   # distancia a la pared de la envolvente
    "suavidad": 0.012,        # radio de la intersección suave con la envolvente
}


def _caja():
    p = np.array(PARAMETROS["eje"])
    r = max(PARAMETROS["radios"]) + 0.02
    return p.min(0) - r, p.max(0) + r


PIEZAS = {"PEL_VEJIGA": {"caja": _caja(), "h": 0.004, "sigma": 0.8, "recorte": None, "voxel": 0.004}}


def forma(objeto_id, p):
    cfg = PARAMETROS
    c, s, sc = P.catmull_rom(cfg["eje"], paso=0.004)
    r = P.perfil_radios(cfg["radios"], s, sc)
    # Aplanamiento dorsoventral alrededor del eje (como el páncreas): se estira z antes de medir.
    zc = np.interp(p[:, 0], c[::-1, 0], c[::-1, 2])
    q = p.copy()
    q[:, 2] = zc + (p[:, 2] - zc) / cfg["aplanamiento"]
    c2 = c.copy()
    return P.sdf_capsulas(q, c2, r) * cfg["aplanamiento"]


def campo(objeto_id, p, ctx=None):
    cfg = PARAMETROS
    F = forma(objeto_id, p)
    x = np.clip(p[:, 0], ref.X_MIN_ENVOLVENTE, ref.X_MAX_ENVOLVENTE)
    pared = ref.distancia_envolvente(x, p[:, 1], p[:, 2]) + cfg["margen_pelvis"]
    return -P.smin(-F, -pared, cfg["suavidad"])
