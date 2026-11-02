"""Pulmones izquierdo y derecho del caballo: ocupan la cavidad pleural y se amoldan al mediastino.

Especificación de la forma (Bradley 1922, fig. 8 p020; Sisson y Grossman; Dyce–Sack–Wensing;
docs/investigacion_anatomica_cavidades_equinas.md §2.2):
- Cada pulmón es un semicono: vértice craneal romo y aplanado lateralmente en la abertura torácica
  (el derecho algo más craneal), borde dorsal largo, grueso y redondeado en el canal costovertebral,
  base caudal.
- Cara costal convexa con impresiones costales suaves.
- Cara diafragmática (base) profundamente cóncava sobre la cúpula. Borde basal agudo que entra en el
  receso costodiafragmático: va de la 6.ª unión costocondral (ventral) a la 16.ª–17.ª costilla (dorsal)
  (referencia.x_borde_basal_pulmon); el receso queda libre.
- Borde ventral delgado con la escotadura cardíaca curva: grande en el izquierdo (costillas 3.ª a 6.ª,
  mitad ventral, deja el corazón contra la pared), pequeña en el derecho (3.º–4.º espacio intercostal).
  Craneal a ella, el lóbulo craneal forma una lengüeta dirigida cranioventralmente.
- Cara mediastínica amoldada al mediastino: impresión cardíaca sobre el pericardio (profunda en el
  izquierdo), surcos abiertos (no túneles) de la aorta, el esófago, las venas cavas y la tráquea; los
  bronquios penetran por el hilio (unión con la tráquea).
- Pulmón derecho: lóbulo accesorio piramidal en el receso mediastínico, caudal al corazón, craneal al
  diafragma y ventral al esófago, que rodea la vena cava caudal por ventral e izquierda (surco abierto
  dorsalmente hacia el pliegue de la vena cava).
- Lobulación poco marcada (sin fisuras profundas): izquierdo con lóbulos craneal y caudal separados solo
  por la escotadura cardíaca; derecho con lóbulos craneal, caudal y accesorio.
"""
import numpy as np

from anatomia import especificacion as E
from anatomia import referencia as ref
from anatomia.organos import contexto as C
from blender import primitivas as P

PARAMETROS = {
    "x_max": {"TOR_PULMON_IZQ": 1.575, "TOR_PULMON_DER": 1.590},  # vértice (el derecho algo más craneal)
    "margen_pared": 0.008,      # pleura costal
    "margen_diafragma": 0.012,  # pleura diafragmática
    "receso": 0.035,            # caída (m) del borde basal hacia medial: borde agudo en el receso
    "mediastino": 0.011,        # semigrosor del mediastino fino (dorsal y ventral)
    # Mediastino craneal ancho (tronco braquiocefálico, vena cava craneal, tráquea): semigrosor máximo y
    # tramo de x en que se ensancha.
    "mediastino_craneal": {"semigrosor": 0.055, "desde": 1.40, "hasta": 1.58},
    # Vértice: cúpula x <= x_max - curvatura * distancia² al eje (y0 = lado * y_eje, z_eje).
    "vertice": {"y_eje": 0.06, "z_eje": 0.33, "curvatura": 2.5},
    "dilatacion_tubos": 0.006,  # separación con tráquea, esófago, aorta y cavas
    "holgura_pericardio": 0.004,
    "redondeo_mediastino": 0.020,  # une los vasos al mediastino (sin láminas de pulmón entre ellos)
    "redondeo": 0.006,
    # Escotadura cardíaca: elipsoide tallado en el borde ventral (centro x, z; semiejes x, y, z).
    "escotadura": {"TOR_PULMON_IZQ": ((1.428, 0.055), (0.082, 0.26, 0.270)),
                   "TOR_PULMON_DER": ((1.468, 0.035), (0.042, 0.18, 0.090))},
    # Lóbulo accesorio (centro y semiejes): entre el pericardio, el diafragma y el esófago.
    "lobulo_accesorio": {"centro": (1.285, 0.002, 0.370), "semiejes": (0.052, 0.060, 0.085)},
    "impresiones_costales": {"profundidad": 0.0016, "ancho": 0.011, "oblicuidad": 0.10},
}

TUBOS_MEDIASTINO = ("TOR_TRAQUEA", "TOR_ESOFAGO", "TOR_AORTA", "TOR_VENA_CAVA_CRANEAL")

PIEZAS = {
    "TOR_PULMON_DER": {"caja": ((0.74, -0.33, -0.03), (1.62, 0.07, 0.72)), "h": 0.006, "sigma": 0.8,
                       "recorte": "toracica", "voxel": 0.005},
    "TOR_PULMON_IZQ": {"caja": ((0.74, 0.0, -0.03), (1.62, 0.33, 0.72)), "h": 0.006, "sigma": 0.8,
                       "recorte": "toracica", "voxel": 0.005},
}


def _smax(a, b, k):
    return -P.smin(-a, -b, k)


def _curvas(oid):
    return C.curvas_de_spec(E.TUBOS[oid])


def _hasta_el_hilio(curvas, x_hilio=1.335):
    """Los bronquios principales solo se tallan hasta el hilio: más allá entran en el pulmón (unión
    tráquea–pulmón) y tallarlos abriría túneles a través del parénquima."""
    salida = [curvas[0]]
    for c, r in curvas[1:]:
        dentro = c[:, 0] >= x_hilio
        salida.append((c[dentro], r[dentro]))
    return salida


_TUBOS = C.campo_tubos(_hasta_el_hilio(_curvas("TOR_TRAQUEA"))
                       + [cr for oid in TUBOS_MEDIASTINO[1:] for cr in _curvas(oid)])
_CAVA_CAUDAL = _curvas("TOR_VENA_CAVA_CAUDAL")[0]


def _ranura_cava(p):
    """Vena cava caudal prolongada dorsalmente en una lámina (pliegue de la vena cava): su surco queda
    abierto hacia el mediastino dorsal y el lóbulo accesorio la rodea sin formar un túnel."""
    c, r = _CAVA_CAUDAL
    orden = np.argsort(c[:, 0])
    z_cava = np.interp(p[:, 0], c[orden, 0], c[orden, 2])
    q = p.copy()
    q[:, 2] = np.minimum(p[:, 2], z_cava)
    return P.sdf_capsulas(q, c, r)


def _impresiones_costales(x, y, z):
    """Surcos suaves de las costillas sobre la cara costal (0 lejos de la pared)."""
    cfg = PARAMETROS["impresiones_costales"]
    acc = np.zeros(len(x))
    for i in range(1, ref.NUM_COSTILLAS + 1):
        xr = ref.x_costilla(i) + cfg["oblicuidad"] * (z - 0.35)
        acc += np.exp(-((x - xr) / cfg["ancho"]) ** 2)
    prof = -ref.distancia_envolvente(x, y, z)
    return cfg["profundidad"] * np.clip(acc, 0, 1) * np.exp(-((prof - PARAMETROS["margen_pared"]) / 0.012) ** 2)


def _cavidad_pleural(objeto_id, p):
    """Hemitórax: dentro de la pared, craneal al diafragma con el receso costodiafragmático libre."""
    cfg = PARAMETROS
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    xc = np.clip(x, ref.X_MIN_ENVOLVENTE, ref.X_MAX_ENVOLVENTE)
    d_pared = ref.distancia_envolvente(xc, y, z)
    xd = ref.x_diafragma(y, z) + cfg["margen_diafragma"]
    # Junto a la pared el pulmón se detiene en el borde basal; hacia medial baja hasta el diafragma.
    receso = np.maximum(ref.x_borde_basal_pulmon(z) - xd, 0.0) * np.exp(np.minimum(d_pared, 0.0) / cfg["receso"])
    base = (xd + receso) - x
    c = _smax(d_pared + cfg["margen_pared"], base, cfg["redondeo"])
    v = cfg["vertice"]
    s = 1.0 if objeto_id == "TOR_PULMON_IZQ" else -1.0
    cupula = cfg["x_max"][objeto_id] - v["curvatura"] * ((y - s * v["y_eje"]) ** 2 + (z - v["z_eje"]) ** 2)
    c = _smax(c, x - cupula, 0.02)
    return c + _impresiones_costales(x, y, z)


def _semigrosor_mediastino(x):
    """Mediastino fino caudalmente y ancho craneal al corazón (vasos de la entrada del tórax)."""
    cfg = PARAMETROS
    mc = cfg["mediastino_craneal"]
    t = np.clip((x - mc["desde"]) / (mc["hasta"] - mc["desde"]), 0.0, 1.0)
    return cfg["mediastino"] + (mc["semigrosor"] - cfg["mediastino"]) * t * t * (3 - 2 * t)


def _lobulo_accesorio(p):
    a = PARAMETROS["lobulo_accesorio"]
    return P.sdf_elipsoide(p, a["centro"], a["semiejes"])


def _escotadura(objeto_id, p, s):
    (xe, ze), semi = PARAMETROS["escotadura"][objeto_id]
    w, _, _ = ref.envolvente(xe)
    return P.sdf_elipsoide(p, (xe, s * float(w), ze), semi)


def campo(objeto_id, p, ctx):
    cfg = PARAMETROS
    izquierdo = objeto_id == "TOR_PULMON_IZQ"
    s = 1.0 if izquierdo else -1.0
    p = np.asarray(p, dtype=float)
    y = p[:, 1]
    hemi = _cavidad_pleural(objeto_id, p)
    lado = _semigrosor_mediastino(p[:, 0]) - s * y  # > 0 en el mediastino y en el otro hemitórax
    acc = _lobulo_accesorio(p)
    F = _smax(hemi, lado, cfg["redondeo"])
    if izquierdo:
        F = _smax(F, 0.006 - acc, cfg["redondeo"])  # el lóbulo accesorio derecho ocupa el receso mediastínico
    else:
        F = P.smin(F, _smax(hemi, acc, 0.01), 0.045)
    # Lo costoso solo cerca del pulmón.
    salida = F.copy()
    cerca = F < 0.03
    q = p[cerca]
    med = _TUBOS(q) - cfg["dilatacion_tubos"]
    med = P.smin(med, _ranura_cava(q) - cfg["dilatacion_tubos"], cfg["redondeo_mediastino"])
    med = P.smin(med, ctx.sdf("TOR_PERICARDIO")(q) - cfg["holgura_pericardio"], cfg["redondeo_mediastino"])
    med = np.minimum(med, ctx.sdf("TOR_CORAZON")(q) - 0.010)
    # Raíz del pulmón: bronquio, arteria y venas pulmonares en un solo muñón tallado en el hilio.
    med = P.smin(med, P.sdf_elipsoide(q, (1.37, s * 0.045, 0.43), (0.045, 0.040, 0.035)), 0.02)
    if izquierdo:
        # El mediastino fino se une a los vasos que lo atraviesan (surcos abiertos, sin túneles).
        med = P.smin(med, np.abs(q[:, 1]) - _semigrosor_mediastino(q[:, 0]), cfg["redondeo_mediastino"])
    Fq = _smax(F[cerca], -med, cfg["redondeo"])
    Fq = _smax(Fq, -_escotadura(objeto_id, q, s), 0.015)
    salida[cerca] = Fq
    return salida


def forma(objeto_id, p):
    return campo(objeto_id, p, C.Contexto())
