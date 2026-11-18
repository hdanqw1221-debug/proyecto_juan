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

-- C07: enlace opcional de cada pregunta con una estructura del modelo 3D.
-- Si se borra la estructura del catalogo, la pregunta se conserva sin enlace.
ALTER TABLE preguntas ADD COLUMN IF NOT EXISTS objeto_id VARCHAR(60);
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_preguntas_objeto') THEN
    ALTER TABLE preguntas ADD CONSTRAINT fk_preguntas_objeto
      FOREIGN KEY (objeto_id) REFERENCES modelos_3d(objeto_id) ON DELETE SET NULL ON UPDATE CASCADE;
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_preguntas_objeto ON preguntas(objeto_id);
