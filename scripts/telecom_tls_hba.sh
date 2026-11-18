#!/usr/bin/env bash
# T08 (cifrado TLS) y T09 (endurecimiento) sobre un clúster PostgreSQL TEMPORAL (puerto 55433), aislado del servidor real.
# Uso: scripts/telecom_tls_hba.sh <carpeta_evidencia> [iteraciones_T02]
set -u
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"; cd "$RAIZ"
OUT="${1:-evidencias/2026-11-18/telecom}"; N="${2:-100}"
PGBIN="/c/Program Files/PostgreSQL/18/bin"
DATA="$RAIZ/build/pg_tmp"; PORT=55433; PW="clave-temporal-$RANDOM$RANDOM"
LAN_IP=$(powershell -NoProfile -Command "(Get-NetIPAddress -AddressFamily IPv4 | Where-Object { \$_.IPAddress -like '10.*' -or \$_.IPAddress -like '192.168.*' } | Where-Object { \$_.IPAddress -ne '192.168.137.1' } | Select-Object -First 1).IPAddress" | tr -d '\r')
mkdir -p "$OUT"; LOG="$OUT/T08_T09_log.txt"; : > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }
export PGPASSWORD="$PW" PGCLIENTENCODING=UTF8
psqlc() { "$PGBIN/psql.exe" -h "$1" -p $PORT -U postgres -d "${2:-postgres}" -At -c "$3" 2>&1; }

cleanup() { "$PGBIN/pg_ctl.exe" -D "$DATA" -m fast stop >/dev/null 2>&1; rm -rf "$DATA" build/pg_pw.txt; }
trap cleanup EXIT
rm -rf "$DATA"; echo "$PW" > build/pg_pw.txt
"$PGBIN/initdb.exe" -D "$DATA" -U postgres --auth=scram-sha-256 --pwfile=build/pg_pw.txt -E UTF8 >/dev/null 2>&1 || { say "initdb fallo"; exit 1; }

# certificado autofirmado (solo para la prueba)
openssl req -new -x509 -days 2 -nodes -subj "//CN=localhost" -out "$DATA/server.crt" -keyout "$DATA/server.key" >/dev/null 2>&1
cat >> "$DATA/postgresql.conf" <<EOF
port = $PORT
listen_addresses = '*'
ssl = on
ssl_cert_file = 'server.crt'
ssl_key_file = 'server.key'
ssl_min_protocol_version = 'TLSv1.2'
EOF
hba() { printf '%s\n' "$@" > "$DATA/pg_hba.conf"; "$PGBIN/pg_ctl.exe" -D "$DATA" reload >/dev/null 2>&1; sleep 1; }

# fase A: admite TLS y no TLS desde 127.0.0.1
hba "host all all 127.0.0.1/32 scram-sha-256" "hostssl all all 127.0.0.1/32 scram-sha-256"
"$PGBIN/pg_ctl.exe" -D "$DATA" -w -l "$DATA/log.txt" start >/dev/null 2>&1 || { say "no arranco el servidor temporal"; exit 1; }
"$PGBIN/createdb.exe" -h 127.0.0.1 -p $PORT -U postgres -E UTF8 gestion_pruebas
"$PGBIN/psql.exe" -h 127.0.0.1 -p $PORT -U postgres -d gestion_pruebas -q -v ON_ERROR_STOP=1 -f db/schema.sql >/dev/null || { say "schema fallo"; exit 1; }
say "== Servidor temporal: $(psqlc 127.0.0.1 postgres 'show ssl') (ssl), $(psqlc 127.0.0.1 postgres 'show ssl_min_protocol_version') minimo"

CP="build/tel;build/classes;lib/gson-2.11.0.jar;lib/jbcrypt-0.4.jar;lib/postgresql-42.7.10.jar"
export APP_DB_PASSWORD="$PW"
for modo in disable require; do
  say "== T08: T02 con sslmode=$modo ($N repeticiones)"
  java -cp "$CP" TelecomDb t02 "tls_$modo" "jdbc:postgresql://127.0.0.1:$PORT/gestion_pruebas?sslmode=$modo" "$OUT" "$N" > "$OUT/T08_t02_$modo.txt" 2>&1
  cat "$OUT/T08_t02_$modo.txt" | tee -a "$LOG" | grep -E "^(conexion|select|login_total|envio)"
  say "   cifrado en la sesion: $(PGSSLMODE=$modo psqlc 127.0.0.1 gestion_pruebas 'select ssl||'"'"' '"'"'||coalesce(version,'"'"'-'"'"')||'"'"' '"'"'||coalesce(cipher,'"'"'-'"'"') from pg_stat_ssl where pid=pg_backend_pid()')"
done

# fase B (T09): solo TLS y solo desde 127.0.0.1; sin regla para el resto
hba "hostssl all all 127.0.0.1/32 scram-sha-256"
say "== T09: pg_hba restringido (hostssl 127.0.0.1/32 unicamente). LAN propia: $LAN_IP"
say "-- (a) 127.0.0.1 con TLS (debe entrar): $(PGSSLMODE=require psqlc 127.0.0.1 postgres 'select 1')"
say "-- (b) 127.0.0.1 SIN TLS (debe rechazarse): $(PGSSLMODE=disable psqlc 127.0.0.1 postgres 'select 1' | head -2 | tr '\n' ' ')"
say "-- (c) desde la IP de la LAN $LAN_IP, origen no autorizado (debe rechazarse): $(PGSSLMODE=require psqlc "$LAN_IP" postgres 'select 1' | head -2 | tr '\n' ' ')"
say "-- (d) contrasena incorrecta desde 127.0.0.1: $(PGPASSWORD=incorrecta PGSSLMODE=require psqlc 127.0.0.1 postgres 'select 1' | head -1)"
say "== Puertos de la IP de la LAN ($LAN_IP), Test-NetConnection (sustituye a nmap)"
powershell -NoProfile -Command "foreach (\$p in 5432,$PORT,80,135,443,445,3389,8080) { \$r = Test-NetConnection -ComputerName $LAN_IP -Port \$p -WarningAction SilentlyContinue -InformationLevel Quiet; '{0}/TCP {1}' -f \$p, \$(if (\$r) {'ABIERTO'} else {'cerrado/filtrado'}) }" 2>&1 | tee -a "$LOG"
