"""Hígado: lámina gruesa adosada a la cara abdominal del diafragma, con lóbulos e impresiones."""
import numpy as np

from anatomia import referencia as ref
from anatomia.organos import contexto as C

PARAMETROS = {
    "separacion": 0.012,
    "grosor_max": 0.09,
    "grosor_min": 0.018,
    "lobulos": [  # (y, z, semieje y, semieje z, factor de grosor)
        (-0.15, 0.42, 0.13, 0.20, 1.0),   # lóbulo derecho
        (0.07, 0.32, 0.12, 0.16, 0.9),    # lóbulos izquierdos (lateral y medial)
        (-0.02, 0.45, 0.08, 0.14, 0.8),   # lóbulos cuadrado y caudado
        (-0.10, 0.56, 0.05, 0.06, 0.7),   # proceso caudado (impresión renal)
    ],
    # Fisuras interlobulares del borde ventral (Bradley 1922, fig. 56): (y, z del fondo, semiancho).
    "fisuras": [(0.125, 0.28, 0.004), (0.025, 0.30, 0.005), (-0.075, 0.28, 0.004)],
    "ensanche_fisura": 0.010,
}

PIEZAS = {
    "ABD_HIGADO": {"caja": ((0.66, -0.31, -0.02), (1.34, 0.22, 0.72)), "h": 0.008, "sigma": 1.2,
                   "recorte": "abdominal"},
}


def sdf_higado(p):
    cfg = PARAMETROS
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    huella = np.full(len(p), 1.0)
    grosor = np.zeros(len(p))
    for y0, z0, ay, az, k in cfg["lobulos"]:
        e2 = ((y - y0) / ay) ** 2 + ((z - z0) / az) ** 2
        huella = np.minimum(huella, (np.sqrt(e2) - 1.0) * min(ay, az))
        grosor = np.maximum(grosor, k * cfg["grosor_max"] * np.sqrt(np.clip(1.0 - e2, 0.0, 1.0)))
    grosor = np.maximum(grosor, cfg["grosor_min"])
    dfdy, dfdz = ref.gradiente_diafragma(y, z)
    estira = np.sqrt(1.0 + dfdy ** 2 + dfdz ** 2)
    x_frente = ref.x_diafragma(y, z) - cfg["separacion"] * estira
    x_dorso = x_frente - grosor * estira
    lamina = np.maximum(x - x_frente, x_dorso - x) / estira
    F = np.maximum(np.maximum(huella, lamina), ref.distancia_envolvente(x, y, z) + 0.01)
    for yf, zf, semi in cfg.get("fisuras", []):  # cuña que se abre hacia el borde ventral
        ancho = semi + cfg.get("ensanche_fisura", 0.01) * np.clip((zf - z) / 0.15, 0.0, 1.0)
        cuna = np.where(z < zf, np.abs(y - yf) - ancho, 1.0)
        F = np.maximum(F, -cuna)
    return F


def forma(objeto_id, p):
    return sdf_higado(p)


def campo(objeto_id, p, ctx):
    """Hígado con impresiones: renal y adrenal derechas, surco de la vena cava y esófago abdominal."""
    F = sdf_higado(p)
    F = np.maximum(F, 0.006 - ctx.sdf("ABD_RINON_DER")(p))
    F = np.maximum(F, 0.004 - ctx.sdf("ABD_ADRENAL_DER")(p))
    tubos = ctx.curvas("ABD_VENA_CAVA_CAUDAL") + list(ctx.esofago_final or [])
    return np.maximum(F, 0.004 - C.campo_tubos(tubos)(p))
