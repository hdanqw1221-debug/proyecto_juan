"""C09: el rol de solo lectura no puede escribir y el visor carga las 47 fichas con él."""
import os
import re
from pathlib import Path

import pytest

psycopg = pytest.importorskip("psycopg")

from visor import repositorio_fichas as rf  # noqa: E402

RAIZ_REPO = Path(__file__).resolve().parents[4]
SQL_ROL = RAIZ_REPO / "db" / "rol_visor_lectura.sql"
ROL = "visor_lectura_prueba"
CLAVE = "clave-de-prueba-c09"
BASE = os.environ.get("APP_TEST_DB_NAME", "gestion_pruebas")


def _dsn(usuario=None, clave=None):
    """DSN de la base de pruebas con las credenciales de la aplicación (administrador) o las dadas."""
    dsn = rf._dsn_desde_properties(RAIZ_REPO / "config" / "app.properties")
    if dsn is None:
        pytest.skip("sin config/app.properties ni APP_DB_PASSWORD: no hay servidor de pruebas configurado")
    dsn = re.sub(r"dbname=\S+", f"dbname={BASE}", dsn)
    if usuario:
        dsn = re.sub(r"user=\S+", f"user={usuario}", dsn)
        dsn = re.sub(r"password=('(\.|[^'])*'|\S+)", f"password={clave}", dsn)
    return dsn


@pytest.fixture(scope="module")
def rol():
    try:
        admin = psycopg.connect(_dsn(), autocommit=True)
        n = admin.execute("SELECT count(*) FROM estructuras_anatomicas").fetchone()[0]
    except Exception as exc:  # sin servidor o sin la base de pruebas
        pytest.skip(f"base de pruebas no disponible: {exc}")
    if n < 47:
        pytest.skip(f"la base de pruebas no tiene las 47 fichas ({n})")
    sql = SQL_ROL.read_text(encoding="utf-8").replace("visor_lectura", ROL)
    admin.execute(sql)
    admin.execute(f"ALTER ROLE {ROL} PASSWORD '{CLAVE}'")
    yield
    admin.execute(f"REVOKE ALL ON ALL TABLES IN SCHEMA public FROM {ROL}")
    admin.execute(f"REVOKE ALL ON SCHEMA public FROM {ROL}")
    admin.execute(f"REVOKE ALL ON DATABASE {BASE} FROM {ROL}")
    admin.execute(f"DROP ROLE IF EXISTS {ROL}")
    admin.close()


def _ejecuta(conn, sql):
    try:
        conn.execute(sql)
        return None
    except psycopg.errors.InsufficientPrivilege as exc:
        conn.rollback()
        return exc


def test_el_rol_no_puede_escribir_ni_ver_datos_de_la_aplicacion(rol):
    with psycopg.connect(_dsn(ROL, CLAVE)) as conn:
        for sql in (
            "INSERT INTO cavidades (codigo, nombre) VALUES ('x', 'x')",
            "UPDATE estructuras_anatomicas SET nombre = 'x'",
            "DELETE FROM modelos_3d",
            "TRUNCATE sistemas_anatomicos",
            "DROP TABLE cavidades",
            "CREATE TABLE intruso (id int)",
            "SELECT * FROM usuarios",
            "SELECT * FROM logs",
            "SELECT * FROM intentos_examen",
        ):
            assert _ejecuta(conn, sql) is not None, f"debió denegarse: {sql}"
        # y la lectura de las cuatro tablas del catálogo sí funciona
        for tabla in ("cavidades", "sistemas_anatomicos", "estructuras_anatomicas", "modelos_3d"):
            assert conn.execute(f"SELECT count(*) FROM {tabla}").fetchone()[0] > 0


def test_el_visor_carga_las_47_fichas_con_el_rol(rol, monkeypatch):
    monkeypatch.setenv("CAVIDADES_DB_DSN", _dsn(ROL, CLAVE))
    repo = rf.RepositorioFichas()
    assert repo.origen == "postgresql", repo.error_bd
    assert len(repo.fichas) == 47
    assert repo.por_objeto, "debe resolver las fichas por objeto 3D"
