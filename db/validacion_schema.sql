-- Migracion de las correcciones de validacion (rama correcciones-validacion, 2026-11-18).
-- Aditiva e idempotente: se puede ejecutar varias veces sobre una base existente.
-- Requiere haber cargado antes db/schema.sql y db/cavidades_schema.sql.
-- Uso: psql -U postgres -d gestion -f db/validacion_schema.sql
SET client_encoding = 'UTF8';

-- C05: eventos del visor 3D, uno por fila, guardados a medida que llegan por la salida estandar del visor.
-- "sesion" agrupa los eventos de una misma ejecucion del visor (inicio ... cierre).
-- t_evento: instante en que el visor emitio el evento (campo "t"); creado_en: instante de la insercion.
CREATE TABLE IF NOT EXISTS exploracion_eventos (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  simulacion_id BIGINT,
  sesion UUID NOT NULL,
  modelo VARCHAR(30),
  evento VARCHAR(20) NOT NULL,
  objeto_id VARCHAR(60),
  ficha_id VARCHAR(60),
  origen VARCHAR(20),
  t_evento TIMESTAMPTZ NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT fk_exploracion_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  CONSTRAINT fk_exploracion_simulacion FOREIGN KEY (simulacion_id) REFERENCES simulaciones(id) ON DELETE SET NULL,
  CONSTRAINT ck_exploracion_evento CHECK (evento IN ('inicio', 'seleccion', 'cierre')),
  CONSTRAINT ck_exploracion_seleccion CHECK (evento <> 'seleccion' OR (objeto_id IS NOT NULL AND ficha_id IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_exploracion_usuario_t ON exploracion_eventos(usuario_id, t_evento);
CREATE INDEX IF NOT EXISTS idx_exploracion_sesion ON exploracion_eventos(sesion);
CREATE INDEX IF NOT EXISTS idx_exploracion_simulacion ON exploracion_eventos(simulacion_id);
