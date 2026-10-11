"""Corazón y pericardio del caballo adulto (~500 kg).

Especificación de la forma (Bradley 1922, fig. 10; Sisson y Grossman; Budras, Anatomy of the Horse;
docs/investigacion_anatomica_cavidades_equinas.md §2.3):
- Posición: base dorsocraneal ~(1,46; 0,02; 0,30-0,37) entre las costillas 3 y 6, ápice romo ~(1,41; 0,037; 0,06)
  a ~2 cm del esternón; ~60 % del volumen a la izquierda del plano medio; eje base-ápice inclinado
  caudoventralmente.
- Masa ventricular: cono romo comprimido lateralmente (barrido de esferas) con el borde caudal (ventrículo
  izquierdo) recto y casi vertical y el borde craneal (ventrículo derecho) convexo; abultamiento craneal
  derecho del ventrículo derecho en semiluna. El ápice lo forma el ventrículo izquierdo.
- Surcos: coronario circular entre atrios y ventrículos (interrumpido solo por el cono arterioso);
  interventricular paraconal en la cara izquierda, desde el surco coronario caudal al cono arterioso, oblicuo
  hacia el ápice y perdido a 2/3; subsinuoso en la cara derecha, bajo la vena cava caudal, más corto.
- Cono arterioso: tracto de salida del ventrículo derecho que asciende por la cara craneal izquierda y se
  continúa con el tronco pulmonar (r 2,5 cm), que corre caudodorsalmente a la izquierda de la raíz aórtica,
  apoyado en el atrio izquierdo, y se divide ventral a la bifurcación traqueal en muñones cortos de las
  arterias pulmonares izquierda y derecha (esta pasa caudal a la aorta y dorsal al atrio izquierdo).
- Bulbo aórtico (r 3 cm) en el centro de la base, a la derecha del tronco pulmonar, hasta el inicio de
  TOR_AORTA (1,44; 0,01; 0,40).
- Atrios: sacos redondeados de pared fina que forman la base, algo más estrechos que los ventrículos (el
  surco coronario queda como un pliegue entrante, sin reborde). Derecho craneal a la derecha, con los muñones
  de las venas cavas craneal y caudal; izquierdo caudodorsal, con cuatro muñones cortos de venas pulmonares.
- Aurículas: bolsas aplanadas en forma de oreja, separadas de la pared que cubren, con el borde libre
  finamente festoneado (muescas someras). La derecha rodea la raíz aórtica por craneal y su punta asoma en la
  cara izquierda, craneal al tronco pulmonar; la izquierda descansa sobre la cara izquierda y el surco
  coronario, caudal al tronco pulmonar, con la punta hacia craneal.
- Muñones vasculares (arterias y venas pulmonares, venas cavas, bulbo aórtico) cortados en plano, como en
  una pieza disecada; los de la aorta y las cavas quedan dentro de los tubos TOR_* que los continúan.
Pericardio: saco fibroso liso y cónico (~1,2 cm de holgura, unión muy suave de las partes dilatadas) que
envuelve todo el corazón y las raíces de los grandes vasos hasta ~4 cm de la base, con el techo redondeado
bajo la tráquea; el ápice se prolonga hasta el esternón (ligamento esternopericárdico).
"""
import numpy as np

from anatomia import especificacion as E
from anatomia.organos import contexto as C
from blender import primitivas as P

PARAMETROS = {
    # Marco: centro del plano valvular (surco coronario) y punta del ápice (eje base -> ápice).
    "centro_coronario": (1.468, 0.016, 0.292),
    "apice": (1.408, 0.037, 0.060),
    # Compresión lateral de los ventrículos (semieje transversal / craneocaudal) alrededor de y0.
    "compresion_lateral": 0.86,
    "y0": 0.014,
    # Masa ventricular: barrido de esferas (centro, radio). Borde caudal x ~1,375; borde craneal convexo.
    "masa_ventricular": [((1.470, 0.014, 0.300), 0.098), ((1.462, 0.018, 0.240), 0.090),
                         ((1.447, 0.025, 0.175), 0.072), ((1.428, 0.032, 0.122), 0.050),
                         ((1.408, 0.037, 0.088), 0.028)],
    # Abultamiento craneal derecho del ventrículo derecho (unión suave, sin pliegue).
    "ventriculo_der": [((1.495, -0.012, 0.285), 0.080), ((1.490, -0.006, 0.215), 0.070),
                       ((1.470, 0.006, 0.150), 0.045)],
    "redondeo_vd": 0.03,
    # Techo ventricular: cúpula baja sobre el plano valvular (altura en el eje y caída por m² de radio).
    "cupula_base": {"altura": 0.014, "caida": 2.2},
    "hombro": 0.03,                       # redondeo del corte por la cúpula valvular
    "surco_coronario": {"profundidad": 0.008, "ancho": 0.014, "altura": 0.0},
    # Surcos interventriculares: puntos guía (se proyectan sobre la superficie), profundidad y semiancho.
    "surco_paraconal": {"guias": [(1.515, 0.084, 0.272), (1.497, 0.092, 0.225), (1.474, 0.089, 0.180),
                                  (1.452, 0.079, 0.140), (1.437, 0.069, 0.115)],
                        "profundidad": 0.006, "ancho": 0.007},
    "surco_subsinuoso": {"guias": [(1.405, -0.046, 0.280), (1.402, -0.044, 0.235), (1.402, -0.036, 0.190),
                                   (1.404, -0.026, 0.160)],
                         "profundidad": 0.006, "ancho": 0.007},
    # Cuerpos atriales: elipsoides en el marco del corazón (semiejes craneal, izquierdo y hacia la base).
    "atrio_der": {"centro": (1.460, -0.036, 0.324), "semiejes": (0.047, 0.028, 0.037)},
    "atrio_izq": {"centro": (1.407, 0.030, 0.332), "semiejes": (0.030, 0.056, 0.034)},
    "redondeo_atrios": 0.022,
    # Relleno de la raíz aórtica entre los atrios, el bulbo y el techo ventricular (centro, semiejes).
    "relleno_raiz": ((1.454, 0.012, 0.328), (0.050, 0.048, 0.046)),
    # Rellenos sin túneles: dorsal al atrio izquierdo bajo la arteria pulmonar derecha, y bajo el arco del
    # tronco pulmonar entre la raíz aórtica y la base de la aurícula izquierda.
    "rellenos": [((1.412, 0.030, 0.372), (0.022, 0.040, 0.020)), ((1.448, 0.064, 0.352), (0.030, 0.024, 0.019))],
    # Aurículas: láminas a lo largo de una curva desde la base (dentro del atrio) hasta la punta. Por punto:
    # centro, semialto (hacia el borde libre), semigrosor y festoneado del borde libre (0-1). El borde libre
    # mira hacia "abajo": la derecha cuelga cranealmente sobre el ventrículo derecho y su borde dorsal se
    # apoya en la raíz aórtica; la izquierda cuelga a lo largo del eje del corazón.
    "abajo_auriculas": {"auricula_der": (0.32, 0.0, -0.95), "auricula_izq": None},
    "auricula_der": [((1.486, -0.050, 0.322), 0.028, 0.012, 0.0), ((1.502, -0.033, 0.319), 0.031, 0.010, 0.4),
                     ((1.513, -0.011, 0.318), 0.031, 0.009, 1.0), ((1.522, 0.011, 0.319), 0.028, 0.008, 1.0),
                     ((1.529, 0.029, 0.318), 0.020, 0.0075, 1.0)],
    "auricula_izq": [((1.412, 0.058, 0.336), 0.020, 0.011, 0.0), ((1.427, 0.088, 0.331), 0.025, 0.009, 0.2),
                     ((1.447, 0.105, 0.320), 0.029, 0.0085, 1.0), ((1.469, 0.107, 0.311), 0.025, 0.008, 1.0),
                     ((1.486, 0.101, 0.304), 0.019, 0.0075, 1.0)],
    # Festones del borde libre: periodo, semiancho de cada muesca y reducción relativa del semialto.
    "festones": {"periodo": 0.019, "ancho_muesca": 0.0045, "amplitud": 0.20},
    "holgura_auriculas": 0.007,           # separación del borde libre respecto a ventrículos y arterias
    # Muñones de las venas cavas en el atrio derecho (inicio dentro, fin cortado en plano, dentro de la vena).
    "munones_cavas": [((1.462, -0.030, 0.360), (1.492, -0.026, 0.358)),
                      ((1.425, -0.030, 0.350), (1.396, -0.032, 0.361))],
    "radio_munon_cava": 0.0235,
    # Vasos (puntos de control y radios); los muñones terminan cortados en plano.
    "bulbo_aortico": {"puntos": [(1.458, 0.004, 0.290), (1.451, 0.006, 0.340), (1.444, 0.009, 0.380),
                                 (1.440, 0.010, 0.400)], "radios": [0.029, 0.031, 0.027, 0.025]},
    # El cono nace enterrado en el ventrículo derecho y emerge poco a poco por el borde craneal.
    "cono_arterioso": {"puntos": [(1.505, 0.034, 0.200), (1.512, 0.050, 0.280), (1.495, 0.062, 0.330)],
                       "radios": [0.026, 0.031, 0.027]},
    "tronco_pulmonar": {"puntos": [(1.495, 0.062, 0.330), (1.474, 0.069, 0.366), (1.446, 0.070, 0.388),
                                   (1.417, 0.062, 0.393)], "radios": [0.026, 0.025, 0.024, 0.022]},
    "arteria_pulmonar_izq": {"puntos": [(1.417, 0.062, 0.393), (1.399, 0.080, 0.395), (1.389, 0.090, 0.395)],
                             "radios": [0.017, 0.016, 0.016]},
    "arteria_pulmonar_der": {"puntos": [(1.412, 0.055, 0.393), (1.400, 0.032, 0.395), (1.397, 0.016, 0.395)],
                             "radios": [0.017, 0.016, 0.016]},
    # Venas pulmonares: muñones cortos (inicio dentro del atrio izquierdo, fin cortado en plano).
    "venas_pulmonares": [((1.396, 0.008, 0.352), (1.372, 0.004, 0.366)),
                         ((1.390, 0.036, 0.354), (1.368, 0.036, 0.367)),
                         ((1.394, 0.066, 0.346), (1.374, 0.078, 0.354)),
                         ((1.390, 0.060, 0.322), (1.370, 0.072, 0.317))],
    "radio_vena_pulmonar": 0.013,
}

PERICARDIO = {
    "holgura": 0.012,       # separación respecto al corazón (m)
    "holgura_vasos": 0.010,  # manga sobre las raíces de los grandes vasos
    "suavizado": 0.04,      # k de la unión suave de las partes del saco
    "techo_z": 0.428,       # reflexión sobre los grandes vasos (bajo la tráquea)
    # Ligamento esternopericárdico: prolongación del ápice hasta el suelo (se recorta con la cavidad).
    "ligamento": {"puntos": [(1.408, 0.037, 0.080), (1.415, 0.035, 0.010)], "radios": [0.022, 0.032]},
}

_CAJA = ((1.33, -0.105, 0.045), (1.60, 0.135, 0.435))
_CAJA_PERICARDIO = ((1.31, -0.12, 0.030), (1.61, 0.155, 0.445))
PIEZAS = {
    # Remallado de Blender más fino que la rejilla: a 3 mm cerraba las rendijas de pocos milímetros entre la
    # punta de las aurículas y el cono arterioso (asas en la malla final).
    "TOR_CORAZON": {"caja": _CAJA, "h": 0.003, "sigma": 1.0, "recorte": "toracica", "voxel": 0.0015},
    "TOR_PERICARDIO": {"caja": _CAJA_PERICARDIO, "h": 0.005, "sigma": 1.5, "recorte": "toracica", "voxel": 0.005,
                       "taubin": 8},
}


# ----------------------------------------------------------------------------
# Utilidades geométricas
# ----------------------------------------------------------------------------
def _smax(a, b, k):
    """Intersección suave."""
    return -P.smin(-a, -b, k)


def _unitario(v):
    v = np.asarray(v, dtype=float)
    return v / np.linalg.norm(v)


_G = np.array(PARAMETROS["centro_coronario"])
_U = _unitario(np.array(PARAMETROS["apice"]) - _G)  # eje base -> ápice (ventral)
# Marco del corazón (columnas): craneal en el plano coronario, izquierda y hacia la base.
_E1 = _unitario(np.array([1.0, 0.0, 0.0]) + _U[0] * (-_U))
_MARCO = np.stack([_E1, np.cross(-_U, _E1), -_U], axis=1)


def _escalar(p):
    """Expande la coordenada transversal para que los barridos de esferas den secciones elípticas."""
    q = np.array(p, dtype=float, copy=True)
    q[..., 1] = PARAMETROS["y0"] + (q[..., 1] - PARAMETROS["y0"]) / PARAMETROS["compresion_lateral"]
    return q


def _curva(puntos, radios, paso=0.006):
    c, s, sc = P.catmull_rom(puntos, paso=paso)
    return c, P.perfil_radios(radios, s, sc)


def _local(p, caja, f, holgura=0.02):
    """Evalúa f solo en los puntos cercanos a `caja`; fuera devuelve la distancia a la caja (cota inferior)."""
    mn, mx = np.asarray(caja[0]) - holgura, np.asarray(caja[1]) + holgura
    salida = P.sdf_caja(p, mn, mx) + holgura
    cerca = np.all((p > mn) & (p < mx), axis=1)
    if np.any(cerca):
        salida[cerca] = f(p[cerca])
    return salida


def _caja_curva(c, r):
    r = np.broadcast_to(np.asarray(r, dtype=float), (len(c),))
    return c.min(0) - r.max(), c.max(0) + r.max()


def _tubo(curva, dilatacion=0.0, cortado=False):
    """Cadena de cápsulas; cortado: el extremo final termina en un plano (muñón de vaso seccionado)."""
    c, r = curva
    r = np.asarray(r) + dilatacion
    caja = _caja_curva(c, r)
    if not cortado:
        return lambda p: _local(p, caja, lambda q: P.sdf_capsulas(q, c, r))
    t = _unitario(c[-1] - c[-2])
    return lambda p: _local(p, caja, lambda q: np.maximum(P.sdf_capsulas(q, c, r), (q - c[-1]) @ t))


def _munones(p, segmentos, radio):
    """Unión de muñones rectos (inicio dentro del órgano) cortados en plano en su extremo."""
    salida = np.full(len(p), np.inf)
    for a, b in segmentos:
        a, b = np.asarray(a, dtype=float), np.asarray(b, dtype=float)
        d = np.maximum(P.sdf_capsulas(p, np.array([a, b]), radio), (p - b) @ _unitario(b - a))
        salida = np.minimum(salida, d)
    return salida


def _preparar_lamina(filas, festones, abajo=None, paso=0.003):
    """Lámina aplanada a lo largo de una curva [(punto, semialto, semigrosor, festoneado)]; el alto sigue la
    dirección `abajo` (por defecto la ventral del eje del corazón)."""
    c, s, sc = P.catmull_rom([f[0] for f in filas], paso=paso)
    t = np.gradient(c, axis=0)
    t /= np.linalg.norm(t, axis=1, keepdims=True)
    v = _U if abajo is None else _unitario(abajo)
    w = v[None, :] - (t @ v)[:, None] * t
    w /= np.linalg.norm(w, axis=1, keepdims=True)
    n = np.cross(t, w)
    W = P.perfil_radios([f[1] for f in filas], s, sc)
    H = P.perfil_radios([f[2] for f in filas], s, sc)
    F = P.perfil_radios([f[3] for f in filas], s, sc)
    caja = _caja_curva(c, np.maximum(W, H))
    return {"c": c, "s": s, "t": t, "w": w, "n": n, "W": W, "H": H, "F": F, "caja": caja, **festones}


def _sdf_lamina(p, L, bloque=20000):
    """Distancia aproximada a una lámina de sección elíptica cuyo borde ventral y punta están festoneados
    (lóbulos anchos separados por muescas redondeadas y someras)."""
    c, t, w, n, s = L["c"], L["t"], L["w"], L["n"], L["s"]
    a, ab = c[:-1], np.diff(c, axis=0)
    l2 = np.sum(ab * ab, axis=1)
    salida = np.empty(len(p))
    ultimo = len(ab) - 1
    for i in range(0, len(p), bloque):
        q = p[i:i + bloque]
        d = q[:, None, :] - a[None]
        tt = np.clip(np.einsum("bsk,sk->bs", d, ab) / l2, 0.0, 1.0)
        dist2 = np.sum((d - tt[..., None] * ab[None]) ** 2, axis=2)
        j = np.argmin(dist2, axis=1)
        tj = tt[np.arange(len(q)), j]
        cp = a[j] + tj[:, None] * ab[j]
        wj = (1 - tj)[:, None] * w[j] + tj[:, None] * w[j + 1]
        nj = (1 - tj)[:, None] * n[j] + tj[:, None] * n[j + 1]
        wj /= np.linalg.norm(wj, axis=1, keepdims=True)
        nj /= np.linalg.norm(nj, axis=1, keepdims=True)
        sj = s[j] + tj * (s[j + 1] - s[j])
        Wj = L["W"][j] + tj * (L["W"][j + 1] - L["W"][j])
        Hj = L["H"][j] + tj * (L["H"][j + 1] - L["H"][j])
        Fj = L["F"][j] + tj * (L["F"][j + 1] - L["F"][j])
        r = q - cp
        xi, eta = np.sum(r * wj, axis=1), np.sum(r * nj, axis=1)
        zeta = np.zeros(len(q))
        m0 = (j == 0) & (tj <= 0.0)
        zeta[m0] = np.maximum(-r[m0] @ t[0], 0.0)
        m1 = (j == ultimo) & (tj >= 1.0)
        zeta[m1] = np.maximum(r[m1] @ t[-1], 0.0)
        lam = L["periodo"]
        dn = np.abs(np.mod(sj + 0.5 * lam, lam) - 0.5 * lam)
        fes = 1.0 - L["amplitud"] * Fj * np.exp(-(dn / L["ancho_muesca"]) ** 2)
        Wv = np.where(xi > 0, Wj * fes, Wj)
        k = np.sqrt((xi / Wv) ** 2 + (eta / Hj) ** 2 + (zeta / np.minimum(Wv, 2.2 * Hj)) ** 2)
        salida[i:i + bloque] = (k - 1.0) * np.minimum(Hj, Wj)
    return salida


def _lamina(filas, festones, abajo=None):
    """Función p -> distancia a la lámina, evaluada solo cerca de ella."""
    L = _preparar_lamina(filas, festones, abajo)
    return lambda p: _local(p, L["caja"], lambda q: _sdf_lamina(q, L))


# Curvas precalculadas (en el espacio expandido para los ventrículos).
def _barrido(clave):
    return _curva(_escalar(np.array([c for c, _ in PARAMETROS[clave]])), [r for _, r in PARAMETROS[clave]])


def _curva_vaso(clave):
    return _curva(PARAMETROS[clave]["puntos"], PARAMETROS[clave]["radios"])


_MASA = _barrido("masa_ventricular")
_VD = _barrido("ventriculo_der")
_BULBO = _tubo(_curva_vaso("bulbo_aortico"), cortado=True)
_CONO = _tubo(_curva_vaso("cono_arterioso"))
_CAVA_CRANEAL = C.campo_tubos(C.curvas_de_spec(E.TUBOS["TOR_VENA_CAVA_CRANEAL"]))
_TRONCO = _tubo(_curva_vaso("tronco_pulmonar"))
_AP_IZQ = _tubo(_curva_vaso("arteria_pulmonar_izq"), cortado=True)
_AP_DER = _tubo(_curva_vaso("arteria_pulmonar_der"), cortado=True)
_AURICULAS = [_lamina(PARAMETROS[c], PARAMETROS["festones"], PARAMETROS["abajo_auriculas"][c])
              for c in ("auricula_der", "auricula_izq")]


# ----------------------------------------------------------------------------
# Partes del corazón
# ----------------------------------------------------------------------------
def _altura_sobre_base(p):
    """Altura (m) sobre el plano valvular, medida hacia la base a lo largo del eje."""
    return (p - _G) @ (-_U)


def _masa_ventricular(p):
    """Cono ventricular cortado por una cúpula baja sobre el plano valvular (sin surcos)."""
    cfg = PARAMETROS
    q = _escalar(p)
    f = P.smin(P.sdf_capsulas(q, *_MASA), P.sdf_capsulas(q, *_VD), cfg["redondeo_vd"]) * 0.93
    rel = p - _G
    rho2 = np.sum(rel * rel, axis=1) - (rel @ _U) ** 2
    cup = cfg["cupula_base"]
    techo = _altura_sobre_base(p) - (cup["altura"] - cup["caida"] * rho2)
    return _smax(f, techo, cfg["hombro"])


def _linea_surco(cfg):
    """Proyecta los puntos guía sobre la masa ventricular (radialmente desde el eje) y los une con una curva."""
    g = np.asarray(cfg["guias"], dtype=float)
    centro = _G + ((g - _G) @ _U)[:, None] * _U
    d = g - centro
    d /= np.linalg.norm(d, axis=1, keepdims=True)
    lo, hi = np.zeros(len(g)), np.full(len(g), 0.25)
    for _ in range(40):
        m = 0.5 * (lo + hi)
        dentro = _masa_ventricular(centro + m[:, None] * d) < 0
        lo, hi = np.where(dentro, m, lo), np.where(dentro, hi, m)
    puntos = centro + lo[:, None] * d
    curva, _, _ = P.catmull_rom(puntos, paso=0.004)
    s = (curva - _G) @ _U
    return curva, float(s.min()), float(s.max())


_SURCOS = [(_linea_surco(PARAMETROS[c]), PARAMETROS[c]) for c in ("surco_paraconal", "surco_subsinuoso")]


def _ventriculos(p, liso=False):
    """Masa ventricular con los surcos coronario e interventriculares (liso: sin surcos)."""
    f = _masa_ventricular(p)
    if liso:
        return f
    sc = PARAMETROS["surco_coronario"]
    f = f + sc["profundidad"] * np.exp(-((_altura_sobre_base(p) - sc["altura"]) / sc["ancho"]) ** 2)
    s = (p - _G) @ _U
    for (curva, s0, s1), cfg in _SURCOS:
        cerca = P.sdf_caja(p, curva.min(0), curva.max(0)) < 3 * cfg["ancho"]
        if not np.any(cerca):
            continue
        d = P.sdf_capsulas(p[cerca], curva, 0.0)
        # El surco se desvanece en su último tercio (hacia el ápice).
        atenua = np.clip((s1 - s[cerca]) / (0.35 * (s1 - s0)), 0.0, 1.0)
        f[cerca] += cfg["profundidad"] * atenua * np.exp(-(d / cfg["ancho"]) ** 2)
    return f


def _atrio(p, clave, dilatacion=0.0):
    a = PARAMETROS[clave]
    return P.sdf_elipsoide(p, a["centro"], np.asarray(a["semiejes"]) + dilatacion, _MARCO)


def _cuerpos_atriales(p):
    """Atrios derecho e izquierdo, relleno de la raíz aórtica y muñones de las venas cavas."""
    cfg = PARAMETROS
    f = P.smin(_atrio(p, "atrio_der"), _atrio(p, "atrio_izq"), cfg["redondeo_atrios"])
    f = P.smin(f, P.sdf_elipsoide(p, *cfg["relleno_raiz"]), 0.015)
    for relleno in cfg["rellenos"]:
        f = P.smin(f, P.sdf_elipsoide(p, *relleno), 0.012)
    return P.smin(f, _cavas(p), 0.008)


def _cavas(p, dilatacion=0.0):
    return _munones(p, PARAMETROS["munones_cavas"], PARAMETROS["radio_munon_cava"] + dilatacion)


def _venas_pulmonares(p, dilatacion=0.0):
    cfg = PARAMETROS
    segs = cfg["venas_pulmonares"]
    pts = np.array([x for s in segs for x in s])
    r = cfg["radio_vena_pulmonar"] + dilatacion
    caja = (pts.min(0) - r, pts.max(0) + r)

    return _local(p, caja, lambda q: _munones(q, segs, r))


def _cota_corazon(p):
    """Cota inferior barata de la distancia al corazón (elipsoide que lo contiene)."""
    return P.sdf_elipsoide(p, (1.460, 0.015, 0.250), (0.17, 0.15, 0.25))


def campo_corazon(p):
    p = np.asarray(p, dtype=float)
    salida = _cota_corazon(p)
    cerca = salida < 0.04
    if not np.any(cerca):
        return salida
    q = p[cerca]
    cfg = PARAMETROS
    ven, cono, bulbo, tronco = _ventriculos(q), _CONO(q), _BULBO(q), _TRONCO(q)
    arterias = np.minimum(_AP_IZQ(q), _AP_DER(q))
    f = P.smin(ven, cono, 0.024)
    f = P.smin(f, bulbo, 0.010)
    f = P.smin(f, tronco, 0.010)
    f = P.smin(f, arterias, 0.010)
    cuerpos = _cuerpos_atriales(q)
    f = P.smin(f, cuerpos, 0.012)
    # Aurículas: libres sobre la pared que cubren (holgura redondeada) y unidas a su atrio por la base.
    obst = np.minimum(np.minimum(ven, cono), np.minimum(tronco, arterias))
    obst = np.minimum(obst, _CAVA_CRANEAL(q))  # la vena entra por el seno de las cavas, no por la aurícula
    aur = np.minimum(_AURICULAS[0](q), _AURICULAS[1](q))
    aur = _smax(aur, cfg["holgura_auriculas"] - obst, 0.006)
    f = P.smin(f, P.smin(aur, cuerpos, 0.02), 0.003)
    f = P.smin(f, _venas_pulmonares(q), 0.008)
    salida[cerca] = f
    return salida


# ----------------------------------------------------------------------------
# Pericardio
# ----------------------------------------------------------------------------
def _envolventes_pericardio(hol, hol_vasos):
    """Partes del saco: tubos dilatados de las raíces vasculares y láminas de las aurículas (sin festones)."""
    tubos = [_tubo(_curva_vaso(c), hol_vasos) for c in ("cono_arterioso", "bulbo_aortico", "tronco_pulmonar",
                                                   "arteria_pulmonar_izq", "arteria_pulmonar_der")]
    sin_festones = {"periodo": 1.0, "ancho_muesca": 1e-3, "amplitud": 0.0}
    laminas = [_lamina([(c, a + hol, g + hol, 0.0) for c, a, g, _ in PARAMETROS[k]], sin_festones,
                       PARAMETROS["abajo_auriculas"][k]) for k in ("auricula_der", "auricula_izq")]
    return tubos + laminas


_ENV_PERICARDIO = _envolventes_pericardio(PERICARDIO["holgura"], PERICARDIO["holgura_vasos"])
_LIGAMENTO = _tubo(_curva(PERICARDIO["ligamento"]["puntos"], PERICARDIO["ligamento"]["radios"]))


def campo_pericardio(p):
    """Saco liso: unión muy suave de las partes del corazón dilatadas, sin surcos ni detalles."""
    pc = PERICARDIO
    hol, k = pc["holgura"], pc["suavizado"]
    p = np.asarray(p, dtype=float)
    salida = _cota_corazon(p) - hol
    cerca = salida < 0.06
    if not np.any(cerca):
        return salida
    q = p[cerca]
    f = _ventriculos(q, liso=True) - hol
    atrios = np.minimum(_atrio(q, "atrio_der", hol), _atrio(q, "atrio_izq", hol))
    atrios = np.minimum(atrios, P.sdf_elipsoide(q, PARAMETROS["relleno_raiz"][0],
                                                np.asarray(PARAMETROS["relleno_raiz"][1]) + hol))
    f = P.smin(f, atrios, k)
    for parte in _ENV_PERICARDIO:
        f = P.smin(f, parte(q), k)
    f = P.smin(f, _cavas(q, pc["holgura_vasos"]), k)
    f = P.smin(f, _venas_pulmonares(q, 0.6 * hol), k)
    f = _smax(f, q[:, 2] - pc["techo_z"], 0.015)
    f = P.smin(f, _LIGAMENTO(q), 0.03)
    salida[cerca] = f
    return salida


# ----------------------------------------------------------------------------
# Interfaz
# ----------------------------------------------------------------------------
def forma(objeto_id, p):
    return campo_corazon(p) if objeto_id == "TOR_CORAZON" else campo_pericardio(p)


def campo(objeto_id, p, ctx=None):
    margen = 0.012 if objeto_id == "TOR_CORAZON" else 0.005
    return np.maximum(forma(objeto_id, p), C.restriccion_cavidad(p, "toracica", margen))
