"""Corazón y pericardio."""
import numpy as np

from anatomia.organos import contexto as C
from blender import primitivas as P

PARAMETROS = {
    "base": (1.48, 0.015, 0.37),
    "apice": (1.39, 0.04, 0.07),
    "radio_max": 0.12,
    "aplanamiento_lateral": 0.88,
    # Aurículas: lóbulos aplanados que abrazan la base cranealmente (centro, semiejes x, y, z).
    "auriculas": [((1.50, -0.048, 0.385), (0.032, 0.050, 0.032)), ((1.49, 0.072, 0.40), (0.030, 0.045, 0.030))],
    # Relieve de superficie según Bradley (1922, fig. 10): surco coronario entre aurículas y ventrículos,
    # surcos interventriculares paraconal (izquierdo) y subsinuoso (derecho) y tronco pulmonar.
    "surco_coronario": {"t": 0.20, "ancho": 0.035, "profundidad": 0.009},
    "surcos_interventriculares": {"azimuts": (0.95, -2.25), "ancho": 0.13, "profundidad": 0.006},
    "tronco_pulmonar": {"inicio": (1.485, 0.03, 0.37), "fin": (1.535, 0.066, 0.478), "radio": 0.021},
    "exponente_apice": 0.80,
    "exponente_base": 0.26,
}
PERICARDIO_SEPARACION = 0.012

_CAJA = ((1.30, -0.16, 0.0), (1.63, 0.18, 0.52))
PIEZAS = {
    "TOR_CORAZON": {"caja": _CAJA, "h": 0.006, "sigma": 1.2, "recorte": "toracica"},
    "TOR_PERICARDIO": {"caja": _CAJA, "h": 0.006, "sigma": 1.2, "recorte": "toracica"},
}


def campo_corazon(p, separacion=0.0):
    C_ = PARAMETROS
    B, A = np.array(C_["base"]), np.array(C_["apice"])
    eje = A - B
    L = np.linalg.norm(eje)
    u = eje / L
    lat = np.array([0.0, 1.0, 0.0]) - u[1] * u
    lat /= np.linalg.norm(lat)
    otro = np.cross(u, lat)
    q = p - B
    a = q @ u
    t = a / L
    rl, ro = q @ lat, q @ otro
    rho = np.sqrt((rl / C_["aplanamiento_lateral"]) ** 2 + ro ** 2)
    tc = np.clip(t, 1e-4, 1 - 1e-4)
    ea, eb = C_.get("exponente_apice", 0.65), C_.get("exponente_base", 0.35)
    norma = eb ** eb * ea ** ea / (eb + ea) ** (eb + ea)  # máximo de t^eb (1-t)^ea
    perfil = C_["radio_max"] * tc ** eb * (1 - tc) ** ea / norma
    fuera_eje = np.where(t < 0, -a, np.where(t > 1, a - L, 0.0))
    F = np.where((t > 0) & (t < 1), rho - perfil, np.sqrt(rho ** 2 + fuera_eje ** 2))
    if separacion == 0.0:  # surcos solo en el corazón; el pericardio es un saco liso
        sc = C_["surco_coronario"]
        F = F + sc["profundidad"] * np.exp(-((t - sc["t"]) / sc["ancho"]) ** 2)
        si = C_["surcos_interventriculares"]
        azim = np.arctan2(ro, rl)
        tramo = np.clip((t - 0.22) / 0.08, 0, 1) * np.clip((0.92 - t) / 0.1, 0, 1)
        for a0 in si["azimuts"]:
            d = np.angle(np.exp(1j * (azim - a0)))
            F = F + si["profundidad"] * tramo * np.exp(-(d / si["ancho"]) ** 2)
    for centro, semiejes in C_["auriculas"]:
        F = P.smin(F, P.sdf_elipsoide(p, centro, semiejes), k=0.04)
    tp = C_["tronco_pulmonar"]
    seg = np.array([tp["inicio"], tp["fin"]])
    F = P.smin(F, P.sdf_capsulas(p, seg, tp["radio"]), k=0.015)
    return F - separacion


def forma(objeto_id, p):
    return campo_corazon(p) if objeto_id == "TOR_CORAZON" else campo_corazon(p, PERICARDIO_SEPARACION)


def campo(objeto_id, p, ctx=None):
    margen = 0.012 if objeto_id == "TOR_CORAZON" else 0.005
    return np.maximum(forma(objeto_id, p), C.restriccion_cavidad(p, "toracica", margen))
