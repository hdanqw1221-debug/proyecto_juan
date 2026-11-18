-- C09: rol de solo lectura para el visor 3D.
-- El visor solo necesita consultar el catalogo anatomico (4 tablas); no debe usar las credenciales de la
-- aplicacion, que pueden escribir en toda la base. Este rol tampoco ve usuarios, notas ni registros.
--
-- Uso (como superusuario, en la base de la aplicacion):
--   psql -U postgres -d gestion -f db/rol_visor_lectura.sql
--   psql -U postgres -d gestion -c "ALTER ROLE visor_lectura PASSWORD '<clave nueva>'"
-- La contrasena NO se guarda en el repositorio. Despues, en el equipo donde corre el visor:
--   $env:CAVIDADES_DB_DSN = "host=localhost port=5432 dbname=gestion user=visor_lectura password=<clave nueva>"
-- Idempotente: se puede ejecutar varias veces.
SET client_encoding = 'UTF8';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'visor_lectura') THEN
    CREATE ROLE visor_lectura LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION CONNECTION LIMIT 50;
  END IF;
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO visor_lectura', current_database());
END $$;

-- Partir de cero: ningun privilegio sobre lo que no sea el catalogo anatomico.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM visor_lectura;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM visor_lectura;
REVOKE CREATE ON SCHEMA public FROM visor_lectura;
GRANT USAGE ON SCHEMA public TO visor_lectura;

GRANT SELECT ON cavidades, sistemas_anatomicos, estructuras_anatomicas, modelos_3d TO visor_lectura;
