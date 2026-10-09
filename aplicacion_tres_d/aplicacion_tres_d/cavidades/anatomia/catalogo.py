"""Catálogo de estructuras: fuente única de identificadores (§4 del prompt).

Cada objeto 3D tiene un `objeto_id` estable (CAVIDAD_ESTRUCTURA[_LADO]) y una
`ficha_id` (compartida, p. ej., por los lados izquierdo y derecho). Estas
propiedades viajan como propiedades personalizadas de Blender, en el
manifiesto del visor y en PostgreSQL.
"""
import re

from blender.materiales import PALETA

CAVIDADES = {
    "toracica": "Cavidad torácica",
    "abdominal": "Cavidad abdominal",
    "pelvica": "Transición pélvica (referencia)",
    "inguinal": "Región inguinal",
    "referencia": "Referencias de orientación",
}

SISTEMAS = {
    "respiratorio": "Aparato respiratorio",
    "cardiovascular": "Sistema cardiovascular",
    "digestivo": "Aparato digestivo y glándulas anexas",
    "linfatico": "Sistema linfático y hematopoyético",
    "urinario": "Aparato urinario",
    "endocrino": "Sistema endocrino",
    "reproductor": "Aparato reproductor masculino",
    "limites": "Límites de las cavidades (serosas, diafragma, conducto inguinal)",
    "referencia": "Referencias topográficas",
}

COLECCIONES = {
    "toracica": "Toracica",
    "abdominal": "Abdominal",
    "pelvica": "Pelvica_referencia",
    "inguinal": "Inguinal",
    "referencia": "Referencias",
}

# Prioridad para resolver colisiones: el de menor prioridad se deforma.
# Orden del prompt: diafragma > corazón > hígado > estómago > ciego > colon > intestino delgado.
PRIORIDAD = {
    "diafragma": 100, "vaso": 90, "corazon": 85, "conducto": 80, "rinon": 75,
    "higado": 70, "bazo": 65, "estomago": 60, "pancreas": 55, "ciego": 50,
    "colon": 45, "colon_menor": 40, "intestino_delgado": 35, "pulmon": 30,
    "pelvico": 95, "inguinal": 95, "referencia": 0,
}

# (objeto_id, ficha_id, nombre, cavidad, sistema, material, capa, clase_prioridad, opciones)
_FILAS = [
    # --- Cavidad torácica -------------------------------------------------
    ("TOR_PULMON_DER", "TOR_PULMON", "Pulmón derecho", "toracica", "respiratorio", "respiratorio", 1, "pulmon", {}),
    ("TOR_PULMON_IZQ", "TOR_PULMON", "Pulmón izquierdo", "toracica", "respiratorio", "respiratorio", 1, "pulmon", {}),
    ("TOR_CORAZON", "TOR_CORAZON", "Corazón", "toracica", "cardiovascular", "corazon", 2, "corazon", {}),
    ("TOR_PERICARDIO", "TOR_PERICARDIO", "Pericardio", "toracica", "cardiovascular", "serosa", 0, "referencia",
     {"opacidad": 0.25, "visible": False}),
    ("TOR_TRAQUEA", "TOR_TRAQUEA", "Tráquea y bronquios principales", "toracica", "respiratorio", "respiratorio", 2, "conducto", {}),
    ("TOR_ESOFAGO", "TOR_ESOFAGO", "Esófago (porción torácica)", "toracica", "digestivo", "digestivo_tubular", 3, "conducto", {}),
    ("TOR_AORTA", "TOR_AORTA", "Aorta torácica", "toracica", "cardiovascular", "arterial", 3, "vaso", {}),
    ("TOR_VENA_CAVA_CRANEAL", "TOR_VENA_CAVA_CRANEAL", "Vena cava craneal", "toracica", "cardiovascular", "venoso", 3, "vaso", {}),
    ("TOR_VENA_CAVA_CAUDAL", "TOR_VENA_CAVA_CAUDAL", "Vena cava caudal (porción torácica)", "toracica", "cardiovascular", "venoso", 3, "vaso", {}),
    ("TOR_DIAFRAGMA", "TOR_DIAFRAGMA", "Diafragma", "toracica", "limites", "serosa", 0, "diafragma", {"opacidad": 0.5}),
    ("TOR_MEDIASTINO", "TOR_MEDIASTINO", "Mediastino", "toracica", "limites", "serosa", 0, "referencia",
     {"opacidad": 0.15, "visible": False}),
    # --- Cavidad abdominal ------------------------------------------------
    ("ABD_ESTOMAGO", "ABD_ESTOMAGO", "Estómago", "abdominal", "digestivo", "estomago", 1, "estomago", {}),
    ("ABD_DUODENO", "ABD_DUODENO", "Duodeno", "abdominal", "digestivo", "digestivo_tubular", 2, "intestino_delgado", {}),
    ("ABD_YEYUNO", "ABD_YEYUNO", "Yeyuno", "abdominal", "digestivo", "digestivo_tubular", 2, "intestino_delgado", {}),
    ("ABD_ILEON", "ABD_ILEON", "Íleon", "abdominal", "digestivo", "digestivo_tubular", 2, "intestino_delgado", {}),
    ("ABD_CIEGO", "ABD_CIEGO", "Ciego", "abdominal", "digestivo", "ciego_colon", 1, "ciego", {}),
    ("ABD_COLON_VENTRAL_DER", "ABD_COLON_VENTRAL_DER", "Colon ventral derecho", "abdominal", "digestivo", "ciego_colon", 1, "colon", {}),
    ("ABD_FLEXURA_ESTERNAL", "ABD_FLEXURA_ESTERNAL", "Flexura esternal", "abdominal", "digestivo", "ciego_colon", 1, "colon", {}),
    ("ABD_COLON_VENTRAL_IZQ", "ABD_COLON_VENTRAL_IZQ", "Colon ventral izquierdo", "abdominal", "digestivo", "ciego_colon", 1, "colon", {}),
    ("ABD_FLEXURA_PELVICA", "ABD_FLEXURA_PELVICA", "Flexura pélvica", "abdominal", "digestivo", "ciego_colon", 1, "colon", {}),
    ("ABD_COLON_DORSAL_IZQ", "ABD_COLON_DORSAL_IZQ", "Colon dorsal izquierdo", "abdominal", "digestivo", "ciego_colon", 2, "colon", {}),
    ("ABD_FLEXURA_DIAFRAGMATICA", "ABD_FLEXURA_DIAFRAGMATICA", "Flexura diafragmática", "abdominal", "digestivo", "ciego_colon", 2, "colon", {}),
    ("ABD_COLON_DORSAL_DER", "ABD_COLON_DORSAL_DER", "Colon dorsal derecho", "abdominal", "digestivo", "ciego_colon", 2, "colon", {}),
    ("ABD_COLON_TRANSVERSO", "ABD_COLON_TRANSVERSO", "Colon transverso", "abdominal", "digestivo", "ciego_colon", 3, "colon", {}),
    ("ABD_COLON_DESCENDENTE", "ABD_COLON_DESCENDENTE", "Colon descendente (colon menor)", "abdominal", "digestivo", "ciego_colon", 2, "colon_menor", {}),
    ("ABD_HIGADO", "ABD_HIGADO", "Hígado", "abdominal", "digestivo", "higado", 1, "higado", {}),
    ("ABD_BAZO", "ABD_BAZO", "Bazo", "abdominal", "linfatico", "bazo", 1, "bazo", {}),
    ("ABD_PANCREAS", "ABD_PANCREAS", "Páncreas", "abdominal", "digestivo", "pancreas", 3, "pancreas", {}),
    ("ABD_RINON_DER", "ABD_RINON_DER", "Riñón derecho", "abdominal", "urinario", "urinario", 3, "rinon", {}),
    ("ABD_RINON_IZQ", "ABD_RINON_IZQ", "Riñón izquierdo", "abdominal", "urinario", "urinario", 3, "rinon", {}),
    ("ABD_ADRENAL_DER", "ABD_ADRENAL", "Glándula adrenal derecha", "abdominal", "endocrino", "pancreas", 3, "rinon", {}),
    ("ABD_ADRENAL_IZQ", "ABD_ADRENAL", "Glándula adrenal izquierda", "abdominal", "endocrino", "pancreas", 3, "rinon", {}),
    ("ABD_URETER_DER", "ABD_URETER", "Uréter derecho", "abdominal", "urinario", "urinario", 3, "conducto", {}),
    ("ABD_URETER_IZQ", "ABD_URETER", "Uréter izquierdo", "abdominal", "urinario", "urinario", 3, "conducto", {}),
    ("ABD_AORTA", "ABD_AORTA", "Aorta abdominal", "abdominal", "cardiovascular", "arterial", 3, "vaso", {}),
    ("ABD_VENA_CAVA_CAUDAL", "ABD_VENA_CAVA_CAUDAL", "Vena cava caudal (porción abdominal)", "abdominal", "cardiovascular", "venoso", 3, "vaso", {}),
    ("ABD_A_MESENTERICA_CRANEAL", "ABD_A_MESENTERICA_CRANEAL", "Arteria mesentérica craneal", "abdominal", "cardiovascular", "arterial", 3, "vaso", {}),
    # --- Transición pélvica -----------------------------------------------
    ("PEL_RECTO", "PEL_RECTO", "Recto", "pelvica", "digestivo", "ciego_colon", 2, "pelvico", {}),
    ("PEL_VEJIGA", "PEL_VEJIGA", "Vejiga urinaria", "pelvica", "urinario", "vejiga", 2, "pelvico", {}),
]

# Región inguinal (macho, ambos lados).
for _lado, _txt in (("IZQ", "izquierdo"), ("DER", "derecho")):
    _FILAS += [
        (f"ING_ANILLO_PROFUNDO_{_lado}", "ING_ANILLO_PROFUNDO", f"Anillo inguinal profundo / vaginal {_txt}", "inguinal", "limites", "serosa", 2, "inguinal", {"sexo": "macho"}),
        (f"ING_CANAL_INGUINAL_{_lado}", "ING_CANAL_INGUINAL", f"Conducto inguinal {_txt}", "inguinal", "limites", "serosa", 1, "inguinal", {"opacidad": 0.35, "sexo": "macho"}),
        (f"ING_ANILLO_SUPERFICIAL_{_lado}", "ING_ANILLO_SUPERFICIAL", f"Anillo inguinal superficial {_txt}", "inguinal", "limites", "serosa", 1, "inguinal", {"sexo": "macho"}),
        (f"ING_CORDON_ESPERMATICO_{_lado}", "ING_CORDON_ESPERMATICO", f"Cordón espermático y túnica vaginal {_txt}", "inguinal", "reproductor", "reproductor", 1, "inguinal", {"opacidad": 0.35, "sexo": "macho"}),
        (f"ING_CONDUCTO_DEFERENTE_{_lado}", "ING_CONDUCTO_DEFERENTE", f"Conducto deferente {_txt}", "inguinal", "reproductor", "reproductor", 2, "inguinal", {"sexo": "macho"}),
        (f"ING_VASOS_TESTICULARES_{_lado}", "ING_VASOS_TESTICULARES", f"Arteria testicular y plexo pampiniforme {_txt}", "inguinal", "cardiovascular", "arterial", 2, "inguinal", {"sexo": "macho"}),
        (f"ING_TESTICULO_{_lado}", "ING_TESTICULO", f"Testículo {_txt}", "inguinal", "reproductor", "reproductor", 1, "inguinal", {"sexo": "macho"}),
        (f"ING_EPIDIDIMO_{_lado}", "ING_EPIDIDIMO", f"Epidídimo {_txt}", "inguinal", "reproductor", "reproductor", 1, "inguinal", {"sexo": "macho"}),
    ]

# Referencias de orientación (no seleccionables, ocultas por defecto).
_FILAS += [
    ("REF_ENVOLVENTE", None, "Envolvente interna de la cavidad", "referencia", "referencia", "referencia", 0, "referencia",
     {"opacidad": 0.08, "visible": False, "seleccionable": False}),
    ("REF_ARCOS_COSTALES", None, "Costillas y arco costal", "referencia", "referencia", "referencia", 0, "referencia",
     {"visible": False, "seleccionable": False}),
    ("REF_LINEAS_TOPOGRAFICAS", None, "Líneas topográficas", "referencia", "referencia", "referencia", 0, "referencia",
     {"visible": False, "seleccionable": False}),
]

PATRON_ID = re.compile(r"^(TOR|ABD|PEL|ING|REF)_[A-Z0-9_]+$")


def _lado_de(objeto_id):
    if objeto_id.endswith("_IZQ"):
        return "IZQ"
    if objeto_id.endswith("_DER"):
        return "DER"
    return "MED"


def _construir():
    objetos = []
    for oid, fid, nombre, cav, sis, mat, capa, clase, opc in _FILAS:
        objetos.append({
            "objeto_id": oid,
            "ficha_id": fid,
            "nombre": nombre,
            "cavidad": cav,
            "sistema": sis,
            "material": mat,
            "color_hex": PALETA[mat],
            "opacidad": float(opc.get("opacidad", 1.0)),
            "capa": int(capa),
            "lado": _lado_de(oid),
            "seleccionable": bool(opc.get("seleccionable", True)),
            "visible_por_defecto": bool(opc.get("visible", True)),
            "coleccion": COLECCIONES[cav],
            "prioridad": PRIORIDAD[clase],
            "sexo_aplicable": opc.get("sexo", "ambos"),
        })
    return objetos


OBJETOS = _construir()
POR_ID = {o["objeto_id"]: o for o in OBJETOS}


def objetos_de_cavidad(cavidad):
    return [o for o in OBJETOS if o["cavidad"] == cavidad]


def fichas_ids():
    """Conjunto de ficha_id usados por objetos seleccionables."""
    return sorted({o["ficha_id"] for o in OBJETOS if o["ficha_id"]})


def objetos_de_ficha(ficha_id):
    return [o["objeto_id"] for o in OBJETOS if o["ficha_id"] == ficha_id]
