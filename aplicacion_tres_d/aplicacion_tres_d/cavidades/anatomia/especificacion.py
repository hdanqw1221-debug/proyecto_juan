"""Especificación geométrica de cada estructura (datos de §4 del prompt).

Coordenadas en metros según `referencia.py`. Las trayectorias son puntos de
control de curvas Catmull-Rom; los radios se interpolan a lo largo del arco.

Las coordenadas del prompt se respetan salvo donde chocan con el contrato de
§2 (diafragma, envolvente) o producen interpenetraciones groseras; cada
cambio queda registrado en AJUSTES con su motivo y se cita en el README.
"""
import numpy as np

from anatomia import referencia as ref

# ----------------------------------------------------------------------------
# Registro de ajustes respecto al prompt (para revisión del director).
# ----------------------------------------------------------------------------
AJUSTES = [
    ("TOR_AORTA", "Arco aórtico desplazado 3–4 cm a la izquierda (y 0,045–0,05).",
     "En el prompt el arco coincidía con el esófago en (1,50; 0,01; 0,50); el documento indica que el esófago pasa a la derecha del arco."),
    ("TOR_ESOFAGO", "Trayecto torácico con y ligeramente negativa (−0,005 a −0,012) y continuación hasta el cardias.",
     "Esófago a la derecha del arco aórtico; el hiato se recalcula con x_diafragma (x ≈ 0,92)."),
    ("TOR_AORTA", "Tronco braquiocefálico ventral a la tráquea.",
     "Evitar que cruce la luz traqueal (relación anatómica normal)."),
    ("TOR_VENA_CAVA_CAUDAL", "Foramen recalculado: x = x_diafragma(−0,04; 0,42) ≈ 1,18.", "Indicado en el prompt."),
    ("ABD_ESTOMAGO", "Saco ciego bajado a z = 0,52 (antes 0,57).",
     "A z = 0,57 quedaba ~6 cm craneal al diafragma, fuera de la cavidad abdominal."),
    ("ABD_CIEGO", "Cuerpo y ápice desplazados hacia el plano medio (y −0,02 a −0,05) y base ligeramente caudal.",
     "En el prompt el cuerpo del ciego ocupaba el mismo volumen que los colones ventral y dorsal derechos (solape de 6–12 cm)."),
    ("ABD_COLON_VENTRAL_DER", "Inicio en el orificio cecocólico (0,66; −0,12; 0,42).", "Continuidad con la base del ciego."),
    ("ABD_COLON_DORSAL_DER", "Extremo caudal en (0,78; −0,15; 0,47).", "Separarlo de la base del ciego."),
    ("ABD_COLON_TRANSVERSO", "Trayecto (0,78; −0,15; 0,47) → (0,78; −0,05; 0,55) → (0,76; 0,06; 0,55).",
     "Continuidad con el colon dorsal derecho ajustado; sigue craneal a la a. mesentérica craneal (x 0,68)."),
    ("ABD_YEYUNO", "Meandro determinista en capas (x 0,43–0,85; y 0,02–0,16; z 0,33–0,51), ~6 m.",
     "Garantiza ausencia de autointersecciones; techo bajado para no invadir riñón izquierdo ni colon transverso."),
    ("ABD_COLON_DESCENDENTE", "Meandro en dos capas (x 0,20–0,56; z 0,445–0,53), ≈2,5 m esquemáticos.",
     "Cuadrante dorsal izquierdo; la longitud real (3–3,5 m) se indica en la ficha."),
    ("ABD_HIGADO", "Proceso caudado añadido como lóbulo en (y −0,10; z 0,56).", "Contacto con el riñón derecho (impresión renal)."),
    ("ING_CONDUCTO_DEFERENTE", "Ampolla a z = 0,375 (antes 0,32); recorrido caudomedial lateral a la vejiga.",
     "A z = 0,32 quedaba dentro de la vejiga."),
    ("ING_TESTICULO", "Centro a z = −0,07 (antes −0,20); estructuras escrotales referidas a él.",
     "Escroto con cuello corto, como en el semental; con −0,20 el cordón medía ~30 cm."),
    ("ING_VASOS_TESTICULARES", "Trayecto pegado a la pared dorsolateral (retroperitoneal).",
     "El trayecto recto cruzaba el colon izquierdo y la flexura pélvica."),
    ("ESTOMAGO_CIEGO_COLON_MAYOR", "Radios ×0,90 (estómago) y ×0,85 (ciego y colon mayor).",
     "Con los radios nominales el tubo digestivo ocuparía ~90 % de la cavidad abdominal; se reduce a ~65 %."),
    ("TODOS_LOS_TUBOS_DIGESTIVOS", "Líneas medias relajadas automáticamente (ver informe de colisiones).",
     "Separación determinista por prioridad y ajuste a la cavidad; el desplazamiento de cada órgano se informa."),
]

# ----------------------------------------------------------------------------
# Cavidad torácica: tubos
# ----------------------------------------------------------------------------
_HIATO_ESO = ref.punto_abertura("hiato_esofagico")
_HIATO_AO = ref.punto_abertura("hiato_aortico")
_FORAMEN_VC = ref.punto_abertura("foramen_vena_cava")

TUBOS = {
    "TOR_TRAQUEA": {
        "recorte": "toracica",
        "partes": [
            {"puntos": [(1.625, 0, 0.42), (1.52, 0, 0.445), (1.40, 0, 0.47)], "radios": 0.028,
             "anillos": (0.05, 0.022)},
            {"puntos": [(1.40, 0, 0.47), (1.30, 0.08, 0.44), (1.15, 0.12, 0.45)], "radios": [0.016, 0.010],
             "anillos": (0.04, 0.018)},
            {"puntos": [(1.40, 0, 0.47), (1.30, -0.08, 0.44), (1.15, -0.12, 0.45)], "radios": [0.016, 0.010],
             "anillos": (0.04, 0.018)},
        ],
    },
    # El extremo caudal del esófago se une al cardias tras la relajación (ver generar_cavidades).
    "TOR_ESOFAGO": {
        "recorte": "envolvente",
        "partes": [{"puntos": [(1.625, 0.03, 0.47), (1.52, -0.005, 0.505), (1.45, -0.012, 0.52),
                               (1.25, -0.005, 0.535), (1.05, 0.01, 0.55), _HIATO_ESO], "radios": 0.015}],
    },
    "TOR_AORTA": {
        "recorte": "envolvente",
        "partes": [
            {"puntos": [(1.44, 0.01, 0.40), (1.49, 0.045, 0.50), (1.46, 0.05, 0.60), (1.35, 0.035, 0.645),
                        (1.00, 0.02, 0.65), (_HIATO_AO[0] + 0.02, 0.01, 0.643)], "radios": 0.025},
            {"puntos": [(1.495, 0.04, 0.52), (1.54, 0.045, 0.44), (1.60, 0.02, 0.37), (1.625, 0.0, 0.34)],
             "radios": 0.014},
        ],
    },
    "TOR_VENA_CAVA_CRANEAL": {
        "recorte": "toracica",
        "partes": [{"puntos": [(1.625, -0.01, 0.35), (1.54, -0.02, 0.355), (1.46, -0.03, 0.36)], "radios": 0.022}],
    },
    "TOR_VENA_CAVA_CAUDAL": {
        "recorte": "envolvente",
        "partes": [{"puntos": [_FORAMEN_VC, (1.30, -0.035, 0.39), (1.42, -0.03, 0.35)], "radios": 0.022}],
    },
    # ------------------------------------------------------------------------
    # Cavidad abdominal: tubos no relajados (vasos y uréteres)
    # ------------------------------------------------------------------------
    "ABD_AORTA": {
        "recorte": "envolvente",
        "partes": [
            {"puntos": [(_HIATO_AO[0] + 0.02, 0.01, 0.643), (0.60, 0.01, 0.62), (0.33, 0.01, 0.58)], "radios": 0.020},
            {"puntos": [(0.33, 0.01, 0.58), (0.25, 0.06, 0.52), (0.18, 0.10, 0.45)], "radios": [0.014, 0.011]},
            {"puntos": [(0.33, 0.01, 0.58), (0.25, -0.04, 0.52), (0.18, -0.10, 0.45)], "radios": [0.014, 0.011]},
        ],
    },
    "ABD_VENA_CAVA_CAUDAL": {
        "recorte": "envolvente",
        "partes": [{"puntos": [(0.33, -0.03, 0.56), (0.70, -0.04, 0.58), (0.95, -0.05, 0.55),
                               (1.07, -0.045, 0.49), _FORAMEN_VC], "radios": 0.022}],
    },
    "ABD_A_MESENTERICA_CRANEAL": {
        "recorte": "envolvente",
        "partes": [{"puntos": [(0.68, 0.01, 0.62), (0.655, 0.005, 0.55), (0.62, 0.0, 0.48)],
                    "radios": [0.012, 0.010]}],
    },
    # Uréteres: el punto inicial se sustituye por el hilio del riñón correspondiente.
    "ABD_URETER_DER": {"recorte": "envolvente", "partes": [{"puntos": [(0.78, -0.065, 0.59), (0.50, -0.06, 0.56),
                                                                      (0.25, -0.06, 0.45), (0.12, -0.03, 0.30)],
                                                           "radios": 0.006}]},
    "ABD_URETER_IZQ": {"recorte": "envolvente", "partes": [{"puntos": [(0.65, 0.065, 0.59), (0.50, 0.06, 0.56),
                                                                      (0.25, 0.06, 0.45), (0.12, 0.03, 0.30)],
                                                           "radios": 0.006}]},
    # ------------------------------------------------------------------------
    # Transición pélvica
    # ------------------------------------------------------------------------
    "PEL_RECTO": {"recorte": None, "partes": [{"puntos": [(0.15, 0, 0.45), (0.0, 0, 0.44), (-0.15, 0, 0.42)],
                                              "radios": 0.05, "haustras": {"amplitud": 0.03, "periodo": 0.06}}]},
}


# ----------------------------------------------------------------------------
# Región inguinal (lado: +1 izquierdo, −1 derecho)
# ----------------------------------------------------------------------------
def _pared(x, z, s, holgura):
    """Punto sobre la pared lateral de la envolvente (lado s), retirado `holgura` hacia dentro."""
    e = ref.EXPONENTE_SECCION
    w, _, _ = ref.envolvente(x)
    zc, hz = ref.centro_y_semialtura(x)
    u = min(abs((z - zc) / hz), 0.97)
    return (x, s * (float(w) * (1 - u ** e) ** (1 / e) - holgura), z)


# Altura del centro del testículo. El prompt lo situaba en z = −0,20 (≈30 cm bajo el conducto inguinal,
# con un cordón desproporcionado); se sube a −0,07 para un escroto con cuello corto, como en el semental.
Z_TESTICULO = -0.07


def inguinal(lado):
    s = 1.0 if lado == "IZQ" else -1.0
    zt = Z_TESTICULO
    anillo_prof = np.array([0.27, 0.11 * s, 0.17])
    anillo_sup = np.array([0.20, 0.09 * s, 0.10])
    eje_canal = anillo_sup - anillo_prof
    testiculo = np.array([0.10, 0.07 * s, zt])
    polo_dorsal = testiculo + np.array([0.0, 0.0, 0.04])
    return {
        "anillo_profundo": {"centro": anillo_prof, "eje": eje_canal, "radio_mayor": 0.025, "radio_menor": 0.006},
        "anillo_superficial": {"centro": anillo_sup, "eje": eje_canal, "radio_mayor": 0.025, "radio_menor": 0.005,
                               "escala_plano": (1.0, 0.4)},
        "canal": {"puntos": [anillo_prof, (anillo_prof + anillo_sup) / 2, anillo_sup], "radios": 0.02},
        "cordon": {"puntos": [anillo_prof, anillo_sup, (0.14, 0.078 * s, 0.03), polo_dorsal + (0.005, 0, 0.01)],
                   "radios": [0.018, 0.018, 0.019, 0.016]},
        # Tras el anillo vaginal gira caudomedialmente (pliegue genital), lateral y luego dorsal a la vejiga.
        "deferente": {"puntos": [(0.045, 0.082 * s, zt - 0.005), (0.07, 0.066 * s, zt + 0.05), (0.12, 0.068 * s, 0.04),
                                 (0.18, 0.082 * s, 0.09), (0.265, 0.104 * s, 0.17), (0.215, 0.088 * s, 0.185),
                                 (0.16, 0.078 * s, 0.24), (0.13, 0.06 * s, 0.335), (0.10, 0.03 * s, 0.375)],
                      "radios": [0.004, 0.004, 0.004, 0.004, 0.004, 0.004, 0.004, 0.009, 0.012]},
        # Arteria testicular: retroperitoneal, pegada a la pared dorsolateral hasta el anillo profundo.
        "vasos": {"puntos": [(0.55, 0.03 * s, 0.60)] + [_pared(x, z, s, 0.007) for x, z in
                                                         ((0.48, 0.57), (0.42, 0.47), (0.36, 0.35), (0.31, 0.25))]
                  + [anillo_prof + (0, 0, -0.005), anillo_sup + (0, 0, -0.005),
                     (0.14, 0.076 * s, 0.03), (0.11, 0.07 * s, zt + 0.05)],
                  "radios": [0.005, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005, 0.007, 0.011]},
        "testiculo": {"centro": testiculo, "semiejes": (0.06, 0.035, 0.04), "inclinacion_grados": 10.0},
        "epididimo": {"puntos": [(0.155, 0.085 * s, zt + 0.025), (0.12, 0.10 * s, zt + 0.035),
                                 (0.08, 0.10 * s, zt + 0.032), (0.045, 0.085 * s, zt + 0.005)],
                      "radios": [0.014, 0.008, 0.008, 0.013]},
    }


# ----------------------------------------------------------------------------
# Cadenas digestivas relajadas (líneas medias continuas)
# ----------------------------------------------------------------------------
def meandro(inicio, x_rango, carriles_y, capas_z, radio, largo_objetivo, semilla, ondula=(0.010, 0.004)):
    """Camino serpenteante determinista por capas y carriles (sin autointersecciones).

    Recorre cada capa z en carriles paralelos a X, alternando sentido; las ondulaciones
    tienen la misma fase en todos los carriles de una capa para no acercarlos entre sí.
    """
    rng = np.random.default_rng(semilla)
    x0, x1 = x_rango
    puntos = [np.asarray(inicio, dtype=float)]
    sentido = -1.0 if abs(inicio[0] - x1) < abs(inicio[0] - x0) else 1.0
    largo = 0.0
    for ic, z in enumerate(capas_z):
        fase_y, fase_z = rng.uniform(0, 2 * np.pi, 2)
        if isinstance(carriles_y[0], (list, tuple)):
            orden = carriles_y[ic]  # carriles explícitos por capa
        else:
            orden = carriles_y if ic % 2 == 0 else carriles_y[::-1]
        for y in orden:
            xs = np.linspace(x0, x1, 40) if sentido > 0 else np.linspace(x1, x0, 40)
            yy = y + ondula[0] * np.sin(2 * np.pi * xs / 0.14 + fase_y)
            zz = z + ondula[1] * np.sin(2 * np.pi * xs / 0.11 + fase_z)
            tramo = np.stack([xs, yy, zz], axis=1)
            puntos.extend(tramo)
            sentido = -sentido
        largo = sum(np.linalg.norm(np.diff(np.array(puntos), axis=0), axis=1))
        if largo >= largo_objetivo:
            break
    p = np.array(puntos)
    # Recorte a la longitud objetivo.
    s = np.concatenate([[0.0], np.cumsum(np.linalg.norm(np.diff(p, axis=0), axis=1))])
    return p[s <= largo_objetivo]


CADENAS = [
    {
        "nombre": "estomago_intestino_delgado",
        "segmentos": [
            {"objeto_id": "ABD_ESTOMAGO",
             "puntos": [(0.88, 0.16, 0.52), (0.94, 0.10, 0.50), (0.98, 0.14, 0.38), (1.00, 0.09, 0.26),
                        (1.02, -0.01, 0.27), (1.02, -0.09, 0.34)],
             "radios": [0.07, 0.10, 0.12, 0.11, 0.07, 0.04], "cardias_indice": 1},
            {"objeto_id": "ABD_DUODENO",
             "puntos": [(1.02, -0.09, 0.34), (1.00, -0.17, 0.40), (0.80, -0.20, 0.55), (0.68, -0.16, 0.57),
                        (0.66, 0.0, 0.58), (0.72, 0.08, 0.57), (0.76, 0.10, 0.55)], "radios": 0.03},
            {"objeto_id": "ABD_YEYUNO", "generador": "yeyuno", "radios": 0.025},
            {"objeto_id": "ABD_ILEON", "generador": "ileon", "radios": [0.025, 0.022]},
        ],
    },
    {
        "nombre": "ciego",
        "segmentos": [
            {"objeto_id": "ABD_CIEGO",
             "puntos": [(0.48, -0.17, 0.52), (0.58, -0.20, 0.53), (0.64, -0.20, 0.40), (0.78, -0.02, 0.25),
                        (0.92, -0.01, 0.14), (1.05, -0.03, 0.065)],
             "radios": [0.12, 0.14, 0.12, 0.11, 0.08, 0.04],
             "haustras": {"amplitud": 0.08, "periodo": 0.08, "n_tenias": 4}},
        ],
    },
    {
        "nombre": "colon",
        "segmentos": [
            {"objeto_id": "ABD_COLON_VENTRAL_DER",
             "puntos": [(0.66, -0.12, 0.42), (0.68, -0.22, 0.28), (0.82, -0.21, 0.13), (1.00, -0.16, 0.08)],
             "radios": [0.10, 0.12], "haustras": {"amplitud": 0.08, "periodo": 0.08, "n_tenias": 4}},
            {"objeto_id": "ABD_FLEXURA_ESTERNAL",
             "puntos": [(1.00, -0.16, 0.08), (1.08, -0.08, 0.07), (1.08, 0.08, 0.07), (1.00, 0.17, 0.08)],
             "radios": 0.11, "haustras": {"amplitud": 0.06, "periodo": 0.08, "n_tenias": 4}},
            {"objeto_id": "ABD_COLON_VENTRAL_IZQ",
             "puntos": [(1.00, 0.17, 0.08), (0.75, 0.21, 0.09), (0.45, 0.18, 0.12), (0.30, 0.12, 0.18)],
             "radios": 0.10, "haustras": {"amplitud": 0.08, "periodo": 0.08, "n_tenias": 4}},
            {"objeto_id": "ABD_FLEXURA_PELVICA",
             "puntos": [(0.30, 0.12, 0.18), (0.20, 0.10, 0.25), (0.22, 0.12, 0.33), (0.30, 0.15, 0.34)],
             "radios": [0.10, 0.07, 0.045, 0.045], "haustras": {"amplitud": 0.0, "periodo": 0.08, "n_tenias": 1}},
            {"objeto_id": "ABD_COLON_DORSAL_IZQ",
             "puntos": [(0.30, 0.15, 0.34), (0.55, 0.20, 0.30), (0.85, 0.18, 0.22), (1.02, 0.14, 0.18)],
             "radios": [0.05, 0.07], "haustras": {"amplitud": 0.0, "periodo": 0.08, "n_tenias": 1}},
            {"objeto_id": "ABD_FLEXURA_DIAFRAGMATICA",
             "puntos": [(1.02, 0.14, 0.18), (1.10, 0.05, 0.22), (1.10, -0.06, 0.24), (1.02, -0.14, 0.28)],
             "radios": 0.08, "haustras": {"amplitud": 0.03, "periodo": 0.08, "n_tenias": 3}},
            {"objeto_id": "ABD_COLON_DORSAL_DER",
             "puntos": [(1.02, -0.14, 0.28), (0.88, -0.19, 0.36), (0.78, -0.15, 0.47)],
             "radios": [0.10, 0.15, 0.06], "haustras": {"amplitud": 0.05, "periodo": 0.09, "n_tenias": 3}},
            {"objeto_id": "ABD_COLON_TRANSVERSO",
             "puntos": [(0.78, -0.15, 0.47), (0.78, -0.05, 0.55), (0.76, 0.06, 0.55)], "radios": 0.05,
             "haustras": {"amplitud": 0.03, "periodo": 0.07, "n_tenias": 3}},
            {"objeto_id": "ABD_COLON_DESCENDENTE", "generador": "descendente", "radios": 0.035,
             "haustras": {"amplitud": 0.12, "periodo": 0.05, "n_tenias": 2}},
        ],
    },
]

# Escala de radios de las vísceras voluminosas. Con los radios nominales del prompt
# el tubo digestivo ocuparía ~90 % de la cavidad abdominal de la envolvente, un
# empaquetado imposible para tubos rígidos (en el animal las vísceras se deforman).
# Se reducen para lograr ~65 % y evitar interpenetraciones; las fichas indican
# las dimensiones reales.
ESCALA_RADIOS = {
    "ABD_ESTOMAGO": 0.90, "ABD_CIEGO": 0.85, "ABD_COLON_VENTRAL_DER": 0.85, "ABD_FLEXURA_ESTERNAL": 0.85,
    "ABD_COLON_VENTRAL_IZQ": 0.85, "ABD_FLEXURA_PELVICA": 0.85, "ABD_COLON_DORSAL_IZQ": 0.85,
    "ABD_FLEXURA_DIAFRAGMATICA": 0.85, "ABD_COLON_DORSAL_DER": 0.85,
}

# Puntos de anclaje (no se mueven durante la relajación).
OSTIUM_ILEAL = (0.62, -0.09, 0.47)
FIN_COLON_DESCENDENTE = (0.15, 0.0, 0.45)


def generador(nombre):
    """Trayectorias densas de los segmentos largos (semillas fijas)."""
    if nombre == "yeyuno":
        inicio = (0.76, 0.10, 0.55)
        m = meandro((0.85, 0.16, 0.51), (0.43, 0.85), [0.16, 0.09, 0.02], [0.51, 0.45, 0.39, 0.33],
                    0.025, 5.9, semilla=11)
        return np.vstack([inicio, m])
    if nombre == "ileon":
        fin_yey = np.asarray(generador("yeyuno")[-1])
        return np.array([fin_yey, (fin_yey[0], -0.02, 0.36), (0.60, -0.05, 0.42), OSTIUM_ILEAL])
    if nombre == "descendente":
        inicio = (0.76, 0.06, 0.55)
        # Tres carriles en la capa dorsal y dos en la ventral: termina en el lado caudal.
        m = meandro((0.56, 0.04, 0.53), (0.20, 0.56), [[0.04, 0.11, 0.18], [0.18, 0.11]], [0.53, 0.445],
                    0.035, 3.0, semilla=23, ondula=(0.008, 0.004))
        return np.vstack([inicio, (0.64, 0.05, 0.56), m, (0.17, 0.05, 0.46), FIN_COLON_DESCENDENTE])
    raise KeyError(nombre)


# Los órganos macizos (corazón, pulmones, hígado, bazo, páncreas, riñones, adrenales, vejiga, estómago
# y diafragma) tienen sus parámetros y su forma en anatomia/organos/.


# Envolturas de los modelos inmersivos (moldes de la cavidad vista desde fuera).
ENVOLTURAS = {
    "profundidad_surco_costal": 0.006,   # impresión de cada costilla en el molde
    "ancho_surco_costal": 0.011,
    "oblicuidad_costal": 0.10,           # el extremo dorsal de la costilla es más craneal
    "pared_inguinal": {"x": (0.13, 0.38), "y_max": 0.19, "grosor": 0.035, "engrosamiento": 0.09},
    "escroto": {"holgura": 0.022, "radio_cuello": 0.032},
}
