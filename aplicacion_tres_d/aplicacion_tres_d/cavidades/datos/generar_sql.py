"""Genera db/cavidades_fichas.sql a partir de las fichas JSON y del manifiesto de modelos.

Las sentencias son idempotentes (INSERT … ON CONFLICT … DO UPDATE), de modo que
el script se puede ejecutar tantas veces como se regeneren las fichas.

Uso: python datos/generar_sql.py [--salida ../../../db/cavidades_fichas.sql]
"""
import argparse
import json
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RAIZ))

from anatomia import catalogo as cat  # noqa: E402

SALIDA_POR_DEFECTO = RAIZ.parents[2] / "db" / "cavidades_fichas.sql"
CAMPOS_TEXTO = ("nombre", "nombre_latino", "descripcion", "topografia", "proyeccion_superficial", "relaciones",
                "medios_de_fijacion", "irrigacion_drenaje", "dimensiones", "consideraciones_clinicas", "exploracion")


def lit(valor):
    """Literal SQL seguro para texto, números, booleanos, NULL y arreglos de texto."""
    if valor is None:
        return "NULL"
    if isinstance(valor, bool):
        return "TRUE" if valor else "FALSE"
    if isinstance(valor, (int, float)):
        return repr(valor)
    if isinstance(valor, (list, tuple)):
        return "ARRAY[" + ", ".join(lit(v) for v in valor) + "]::text[]" if valor else "ARRAY[]::text[]"
    return "'" + str(valor).replace("'", "''") + "'"


def generar(fichas, manifiesto):
    l = ["-- Generado por cavidades/datos/generar_sql.py. No editar a mano.", "BEGIN;", ""]
    for codigo, nombre in cat.CAVIDADES.items():
        l.append(f"INSERT INTO cavidades (codigo, nombre) VALUES ({lit(codigo)}, {lit(nombre)}) "
                 "ON CONFLICT (codigo) DO UPDATE SET nombre = EXCLUDED.nombre;")
    for codigo, nombre in cat.SISTEMAS.items():
        l.append(f"INSERT INTO sistemas_anatomicos (codigo, nombre) VALUES ({lit(codigo)}, {lit(nombre)}) "
                 "ON CONFLICT (codigo) DO UPDATE SET nombre = EXCLUDED.nombre;")
    l.append("")
    for f in fichas:
        cols = ["ficha_id", *CAMPOS_TEXTO, "cavidad_id", "sistema_id", "referencias", "pendiente_validacion",
                "campos_pendientes", "sexo_aplicable"]
        vals = [lit(f["ficha_id"]), *(lit(f[c]) for c in CAMPOS_TEXTO),
                f"(SELECT id FROM cavidades WHERE codigo = {lit(f['cavidad'])})",
                f"(SELECT id FROM sistemas_anatomicos WHERE codigo = {lit(f['sistema'])})",
                lit(f["referencias"]), lit(f["pendiente_validacion"]), lit(f.get("campos_pendientes", [])),
                lit(f.get("sexo_aplicable", "ambos"))]
        act = ", ".join(f"{c} = EXCLUDED.{c}" for c in cols[1:])
        l.append(f"INSERT INTO estructuras_anatomicas ({', '.join(cols)})\nVALUES ({', '.join(vals)})\n"
                 f"ON CONFLICT (ficha_id) DO UPDATE SET {act};")
    l.append("")
    for o in manifiesto["objetos"]:
        cols = ["objeto_id", "ficha_id", "nombre", "cavidad_id", "lado", "archivo", "color_hex", "opacidad", "capa",
                "seleccionable", "visible_por_defecto"]
        vals = [lit(o["objeto_id"]), lit(o["ficha_id"]), lit(o["nombre"]),
                f"(SELECT id FROM cavidades WHERE codigo = {lit(o['cavidad'])})", lit(o["lado"]), lit(o["archivo"]),
                lit(o["color_hex"]), lit(float(o["opacidad"])), lit(int(o["capa"])), lit(o["seleccionable"]),
                lit(o["visible_por_defecto"])]
        act = ", ".join(f"{c} = EXCLUDED.{c}" for c in cols[1:])
        l.append(f"INSERT INTO modelos_3d ({', '.join(cols)}) VALUES ({', '.join(vals)}) "
                 f"ON CONFLICT (objeto_id) DO UPDATE SET {act};")
    # Prácticas 3D vinculadas a la materia de ejemplo (solo si existe y aún no están creadas).
    practicas = [
        ("Practica 3D: Cavidades toracica, abdominal y region inguinal", "cavidades_equinas.py",
         "Exploracion de las visceras del equino con seleccion por clic y ficha anatomica.",
         "Identificar las visceras de las cavidades toracica y abdominal y de la region inguinal"),
        ("Practica 3D inmersiva: Cavidad toracica", "cavidad_toracica.py",
         "La cavidad toracica se separa en sus componentes al acercarse.",
         "Reconocer pulmones y corazon junto con grandes vasos y diafragma"),
        ("Practica 3D inmersiva: Cavidad abdominal", "cavidad_abdominal.py",
         "La cavidad abdominal se separa en sus componentes al acercarse.",
         "Reconocer el tubo digestivo con sus glandulas anexas y el aparato urinario"),
        ("Practica 3D inmersiva: Region inguinal", "region_inguinal.py",
         "La region inguinal y el escroto se separan en sus componentes al acercarse.",
         "Reconocer el conducto inguinal y el cordon espermatico hasta el testiculo"),
    ]
    for nombre, script, descripcion, objetivo in practicas:
        config = {
            "habilitado": True, "tema": "cavidades_equinas", "nivel": "intermedio", "script3d": script,
            "tiempoMinutos": 45,
            "objetivos": [objetivo, "Relacionar cada estructura con su topografia y proyeccion superficial"],
            "instrucciones": ["Inicia el visor 3D", "Acercate con la rueda del raton para separar los componentes",
                              "Haz clic en una estructura para ver su ficha completa"],
            "escenario": {"titulo": nombre.split(": ", 1)[1], "descripcion": descripcion},
        }
        l += ["",
              "INSERT INTO simulaciones (curso_id, nombre, descripcion, configuracion)",
              f"SELECT c.id, {lit(nombre)}, {lit(descripcion)},",
              f"       {lit(json.dumps(config, ensure_ascii=True))}::jsonb",
              "FROM cursos c WHERE c.nombre = 'Anatomia Veterinaria Equina I'",
              f"AND NOT EXISTS (SELECT 1 FROM simulaciones s WHERE s.curso_id = c.id AND s.nombre = {lit(nombre)});"]
    l += ["", "COMMIT;"]
    return "\n".join(l) + "\n"


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--fichas", default=str(RAIZ / "datos" / "fichas_anatomicas.json"))
    ap.add_argument("--manifiesto", default=str(RAIZ / "modelos" / "manifiesto.json"))
    ap.add_argument("--salida", default=str(SALIDA_POR_DEFECTO))
    a = ap.parse_args()
    fichas = json.loads(Path(a.fichas).read_text(encoding="utf-8"))
    manifiesto = json.loads(Path(a.manifiesto).read_text(encoding="utf-8"))
    Path(a.salida).write_text(generar(fichas, manifiesto), encoding="utf-8")
    print(f"Escrito {a.salida}: {len(fichas)} fichas, {len(manifiesto['objetos'])} objetos")
