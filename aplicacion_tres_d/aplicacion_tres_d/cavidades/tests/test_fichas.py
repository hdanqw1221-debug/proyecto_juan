"""Pruebas de las fichas anatómicas (§5 y §8)."""
import json
from pathlib import Path

import jsonschema
import pytest

from anatomia import catalogo as cat

DATOS = Path(__file__).resolve().parents[1] / "datos"
CAMPOS_CONTEO = ("nombre", "nombre_latino", "descripcion", "topografia", "proyeccion_superficial", "relaciones",
                 "medios_de_fijacion", "irrigacion_drenaje", "dimensiones", "consideraciones_clinicas", "exploracion")


@pytest.fixture(scope="module")
def fichas():
    return json.loads((DATOS / "fichas_anatomicas.json").read_text(encoding="utf-8"))


def test_valida_contra_esquema(fichas):
    esquema = json.loads((DATOS / "fichas.schema.json").read_text(encoding="utf-8"))
    jsonschema.validate(fichas, esquema)


def test_longitud_entre_120_y_300_palabras(fichas):
    for f in fichas:
        n = sum(len(f[c].split()) for c in CAMPOS_CONTEO)
        assert 120 <= n <= 300, (f["ficha_id"], n)


def test_coincide_con_catalogo(fichas):
    ids = [f["ficha_id"] for f in fichas]
    assert len(ids) == len(set(ids))
    assert set(ids) == set(cat.fichas_ids())
    for f in fichas:
        assert sorted(f["objetos"]) == sorted(cat.objetos_de_ficha(f["ficha_id"]))
        meta = cat.POR_ID[f["objetos"][0]]
        assert f["cavidad"] == meta["cavidad"]


def test_campos_pendientes_coherentes(fichas):
    for f in fichas:
        pendientes = f.get("campos_pendientes", [])
        assert bool(pendientes) == f["pendiente_validacion"] or (f["pendiente_validacion"] and not pendientes)
        for c in pendientes:
            assert c in f, (f["ficha_id"], c)


def test_sql_generado_coincide_con_json(fichas, tmp_path):
    from datos.generar_sql import generar

    manifiesto = json.loads((DATOS.parent / "modelos" / "manifiesto.json").read_text(encoding="utf-8"))
    sql = generar(fichas, manifiesto)
    for f in fichas:
        assert f"VALUES ('{f['ficha_id']}'" in sql
    assert sql.count("INSERT INTO modelos_3d") == len(manifiesto["objetos"])
    assert sql.strip().endswith("COMMIT;")
