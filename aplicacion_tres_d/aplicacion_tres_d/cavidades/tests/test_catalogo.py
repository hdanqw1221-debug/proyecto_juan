"""Pruebas del catálogo de estructuras (§8)."""
from anatomia import catalogo as cat


def test_ids_unicos_y_nomenclatura():
    ids = [o["objeto_id"] for o in cat.OBJETOS]
    assert len(ids) == len(set(ids))
    for oid in ids:
        assert cat.PATRON_ID.match(oid), oid
        assert oid == oid.upper() and oid.isascii()


def test_prefijo_coincide_con_cavidad():
    prefijo = {"TOR": "toracica", "ABD": "abdominal", "PEL": "pelvica", "ING": "inguinal", "REF": "referencia"}
    for o in cat.OBJETOS:
        assert prefijo[o["objeto_id"].split("_")[0]] == o["cavidad"]


def test_seleccionables_tienen_ficha_y_referencias_no():
    for o in cat.OBJETOS:
        if o["cavidad"] == "referencia":
            assert not o["seleccionable"] and o["ficha_id"] is None
        else:
            assert o["seleccionable"] and o["ficha_id"]


def test_cada_ficha_tiene_objetos():
    for fid in cat.fichas_ids():
        assert cat.objetos_de_ficha(fid)


def test_campos_validos():
    for o in cat.OBJETOS:
        assert o["sistema"] in cat.SISTEMAS
        assert o["lado"] in ("IZQ", "DER", "MED")
        assert 0 <= o["capa"] <= 3
        assert 0.0 < o["opacidad"] <= 1.0
        assert len(o["color_hex"]) == 7 and o["color_hex"].startswith("#")


def test_inguinal_bilateral():
    ing = [o["objeto_id"] for o in cat.objetos_de_cavidad("inguinal")]
    assert len(ing) == 16
    for oid in ing:
        otro = oid.replace("_IZQ", "_DER") if oid.endswith("_IZQ") else oid.replace("_DER", "_IZQ")
        assert otro in cat.POR_ID
