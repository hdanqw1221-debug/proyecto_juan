"""Bloque 0 y C09: cadena de conexión del visor sin credenciales en el repositorio."""
import pytest

from visor import repositorio_fichas as rf


def _props(tmp_path, clave="del-archivo"):
    ruta = tmp_path / "app.properties"
    ruta.write_text(
        "app.db.url=jdbc:postgresql://localhost:5432/gestion\n"
        "app.db.user=postgres\n"
        f"app.db.password={clave}\n",
        encoding="utf-8",
    )
    return ruta


def test_variable_de_entorno_tiene_prioridad(tmp_path, monkeypatch):
    monkeypatch.setenv("APP_DB_PASSWORD", "del-entorno")
    dsn = rf._dsn_desde_properties(_props(tmp_path))
    assert "password=del-entorno" in dsn
    assert "del-archivo" not in dsn


def test_sin_variable_usa_el_archivo(tmp_path, monkeypatch):
    monkeypatch.delenv("APP_DB_PASSWORD", raising=False)
    assert "password=del-archivo" in rf._dsn_desde_properties(_props(tmp_path))


def test_claves_con_espacios_y_comillas_se_entrecomillan(tmp_path, monkeypatch):
    monkeypatch.delenv("APP_DB_PASSWORD", raising=False)
    conninfo = pytest.importorskip("psycopg.conninfo")
    clave = "a b'c" + chr(92) + "d"  # espacio, comilla simple y barra invertida
    dsn = rf._dsn_desde_properties(_props(tmp_path, clave=clave))
    assert conninfo.conninfo_to_dict(dsn)["password"] == clave


def test_sin_archivo_no_hay_dsn(tmp_path):
    assert rf._dsn_desde_properties(tmp_path / "no-existe.properties") is None
