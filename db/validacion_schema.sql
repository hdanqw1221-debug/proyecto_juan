-- Migracion de las correcciones de validacion (rama correcciones-validacion, 2026-11-18).
-- Aditiva e idempotente: se puede ejecutar varias veces sobre una base existente.
-- Requiere haber cargado antes db/schema.sql y db/cavidades_schema.sql.
-- Uso: psql -U postgres -d gestion -f db/validacion_schema.sql
SET client_encoding = 'UTF8';
