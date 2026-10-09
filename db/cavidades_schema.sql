-- Migración: catálogo anatómico del simulador de cavidades equinas.
-- Es aditiva e idempotente: no modifica tablas existentes.
-- Uso: psql -U postgres -d gestion -f db/cavidades_schema.sql
--      psql -U postgres -d gestion -f db/cavidades_fichas.sql   (generado por cavidades/datos/generar_sql.py)

CREATE TABLE IF NOT EXISTS cavidades (
  id BIGSERIAL PRIMARY KEY,
  codigo VARCHAR(30) NOT NULL UNIQUE,
  nombre VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS sistemas_anatomicos (
  id BIGSERIAL PRIMARY KEY,
  codigo VARCHAR(30) NOT NULL UNIQUE,
  nombre VARCHAR(120) NOT NULL
);

-- Una fila por ficha anatómica (ficha_id estable compartido por los objetos 3D).
CREATE TABLE IF NOT EXISTS estructuras_anatomicas (
  ficha_id VARCHAR(60) PRIMARY KEY,
  nombre VARCHAR(160) NOT NULL,
  nombre_latino VARCHAR(200),
  cavidad_id BIGINT NOT NULL,
  sistema_id BIGINT NOT NULL,
  descripcion TEXT NOT NULL,
  topografia TEXT,
  proyeccion_superficial TEXT,
  relaciones TEXT,
  medios_de_fijacion TEXT,
  irrigacion_drenaje TEXT,
  dimensiones TEXT,
  consideraciones_clinicas TEXT,
  exploracion TEXT,
  referencias TEXT[],
  pendiente_validacion BOOLEAN NOT NULL DEFAULT TRUE,
  campos_pendientes TEXT[],
  sexo_aplicable VARCHAR(10) NOT NULL DEFAULT 'ambos',
  CONSTRAINT fk_estructura_cavidad FOREIGN KEY (cavidad_id) REFERENCES cavidades(id) ON DELETE RESTRICT,
  CONSTRAINT fk_estructura_sistema FOREIGN KEY (sistema_id) REFERENCES sistemas_anatomicos(id) ON DELETE RESTRICT,
  CONSTRAINT ck_estructura_sexo CHECK (sexo_aplicable IN ('ambos', 'macho', 'hembra'))
);

CREATE INDEX IF NOT EXISTS idx_estructuras_cavidad ON estructuras_anatomicas(cavidad_id);
CREATE INDEX IF NOT EXISTS idx_estructuras_sistema ON estructuras_anatomicas(sistema_id);

-- Una fila por objeto 3D del manifiesto. Las referencias de orientación no tienen ficha.
CREATE TABLE IF NOT EXISTS modelos_3d (
  objeto_id VARCHAR(60) PRIMARY KEY,
  ficha_id VARCHAR(60),
  nombre VARCHAR(160) NOT NULL,
  cavidad_id BIGINT NOT NULL,
  lado VARCHAR(3) NOT NULL,
  archivo VARCHAR(255) NOT NULL,
  color_hex CHAR(7) NOT NULL,
  opacidad REAL NOT NULL,
  capa SMALLINT NOT NULL,
  seleccionable BOOLEAN NOT NULL,
  visible_por_defecto BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_modelo_ficha FOREIGN KEY (ficha_id) REFERENCES estructuras_anatomicas(ficha_id) ON DELETE CASCADE,
  CONSTRAINT fk_modelo_cavidad FOREIGN KEY (cavidad_id) REFERENCES cavidades(id) ON DELETE RESTRICT,
  CONSTRAINT ck_modelo_lado CHECK (lado IN ('IZQ', 'DER', 'MED')),
  CONSTRAINT ck_modelo_opacidad CHECK (opacidad > 0 AND opacidad <= 1),
  CONSTRAINT ck_modelo_capa CHECK (capa BETWEEN 0 AND 3)
);

CREATE INDEX IF NOT EXISTS idx_modelos_3d_ficha ON modelos_3d(ficha_id);
