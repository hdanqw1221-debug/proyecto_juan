"""Fuente de las fichas anatómicas: PostgreSQL si hay conexión, si no el JSON local.

La conexión se toma de `CAVIDADES_DB_DSN` (cadena libpq; se recomienda el rol de solo
lectura de `db/rol_visor_lectura.sql`) o de `proyecto_juan/config/app.properties`. Si existe
la variable `APP_DB_PASSWORD`, tiene prioridad sobre la contraseña del archivo.
El driver (`psycopg` 3 o `psycopg2`) es opcional.
"""
import json
import os
import re
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
RUTA_JSON = RAIZ / "datos" / "fichas_anatomicas.json"
RUTA_CONFIG = RAIZ.parents[2] / "config" / "app.properties"

CONSULTA = """
SELECT e.ficha_id, e.nombre, e.nombre_latino, c.codigo AS cavidad, s.codigo AS sistema, e.descripcion,
       e.topografia, e.proyeccion_superficial, e.relaciones, e.medios_de_fijacion, e.irrigacion_drenaje,
       e.dimensiones, e.consideraciones_clinicas, e.exploracion, e.referencias, e.pendiente_validacion,
       e.campos_pendientes, e.sexo_aplicable,
       ARRAY(SELECT m.objeto_id FROM modelos_3d m WHERE m.ficha_id = e.ficha_id ORDER BY m.objeto_id) AS objetos
FROM estructuras_anatomicas e
JOIN cavidades c ON c.id = e.cavidad_id
JOIN sistemas_anatomicos s ON s.id = e.sistema_id
"""


def _dsn_desde_properties(ruta):
    if not Path(ruta).exists():
        return None
    props = {}
    for linea in Path(ruta).read_text(encoding="utf-8", errors="ignore").splitlines():
        if "=" in linea and not linea.strip().startswith("#"):
            k, v = linea.split("=", 1)
            props[k.strip()] = v.strip()
    url = props.get("app.db.url") or props.get("db.url")
    m = re.match(r"jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?]+)", url or "")
    if not m:
        return None
    host, puerto, base = m.group(1), m.group(2) or "5432", m.group(3)
    usuario = props.get("app.db.user") or props.get("db.user") or "postgres"
    clave = os.environ.get("APP_DB_PASSWORD") or props.get("app.db.password") or props.get("db.password") or ""
    partes = {"host": host, "port": puerto, "dbname": base, "user": usuario, "password": clave}
    return " ".join(f"{k}={_valor_libpq(v)}" for k, v in partes.items()) + " connect_timeout=2"


def _valor_libpq(valor):
    """Entrecomilla un valor de cadena libpq (espacios, comillas y barras invertidas)."""
    valor = str(valor)
    if valor and not re.search(r"[\s'\\]", valor):
        return valor
    return "'" + valor.replace("\\", "\\\\").replace("'", "\\'") + "'"


def _conectar(dsn):
    try:
        import psycopg
        return psycopg.connect(dsn)
    except ImportError:
        import psycopg2
        return psycopg2.connect(dsn)


class RepositorioFichas:
    """Fichas por ficha_id y por objeto_id. `origen` indica de dónde se cargaron."""

    def __init__(self, ruta_json=RUTA_JSON, ruta_config=RUTA_CONFIG, usar_bd=True):
        self.fichas = {}
        self.origen = "json"
        self.error_bd = None
        dsn = os.environ.get("CAVIDADES_DB_DSN") or _dsn_desde_properties(ruta_config)
        if usar_bd and dsn:
            try:
                self._cargar_bd(dsn)
                self.origen = "postgresql"
            except Exception as exc:  # sin driver, sin servidor o sin tablas: se usa el JSON
                self.error_bd = f"{type(exc).__name__}: {exc}"
                self.fichas = {}
        if not self.fichas:
            self._cargar_json(ruta_json)
        self.por_objeto = {oid: f for f in self.fichas.values() for oid in f.get("objetos", [])}

    def _cargar_json(self, ruta):
        for f in json.loads(Path(ruta).read_text(encoding="utf-8")):
            self.fichas[f["ficha_id"]] = f

    def _cargar_bd(self, dsn):
        with _conectar(dsn) as conn:
            cur = conn.cursor()
            cur.execute(CONSULTA)
            columnas = [d[0] for d in cur.description]
            for fila in cur.fetchall():
                f = dict(zip(columnas, fila))
                f["referencias"] = list(f["referencias"] or [])
                f["campos_pendientes"] = list(f["campos_pendientes"] or [])
                f["objetos"] = list(f["objetos"] or [])
                self.fichas[f["ficha_id"]] = f

    def ficha(self, ficha_id):
        return self.fichas.get(ficha_id)

    def ficha_de_objeto(self, objeto_id):
        return self.por_objeto.get(objeto_id)
