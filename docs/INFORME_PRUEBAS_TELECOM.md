# Informe de pruebas de telecomunicaciones

Fecha: 18 de noviembre de 2026 · Rama `correcciones-validacion` · CSV crudos en `evidencias/2026-11-18/telecom/`.

**Alcance honesto.** Se pidió medir T01–T10 con un tiempo muy limitado. Se ejecutaron **T02 y T03 (versiones reducidas)** y se dejó el
código fuente de **T01**. **T04–T10 no se ejecutaron.** Todo se midió en **un solo equipo, por la interfaz de bucle local
(`localhost`)**: no hay red real, por lo que las latencias son un límite inferior y no representan una sala de cómputo.

## Montaje

| Elemento | Valor |
|---|---|
| Equipos | 1 (cliente y servidor juntos). Sin enlace físico: bucle local `127.0.0.1` |
| Servidor | PostgreSQL 18.3 en `localhost:5432`, base aislada `gestion_pruebas` (la base `gestion` no se tocó) |
| Cliente | Aplicación Java (JDBC sin *pool*: cada operación abre su propia conexión), BCrypt coste 12 |
| Máquina | Intel Core i7-4600U, 2 núcleos, 2,10 GHz, Windows 10 LTSC 2019. Cliente y servidor **compiten por los mismos 2 núcleos** |
| Herramienta | `tools/TelecomDb.java` (datos temporales creados y borrados) |

## T01 · Diagrama de red y de servicios

Fuente PlantUML: `docs/diagramas/T01_red_y_servicios.puml` (equipos, procesos, puerto 5432/TCP, HTTP propuesto, JDBC, psycopg,
tubería stdout JSON). **El PNG no se generó**: la descarga de PlantUML falló por red (tiempo agotado y conexión restablecida).
Estado: **parcial**.

## T02 · Latencia de la base de datos (bucle local, 200 repeticiones por operación)

| Operación | n | media (ms) | mediana (ms) | p95 (ms) | mín (ms) | máx (ms) |
|---|---|---|---|---|---|---|
| conexion_jdbc | 200 | 71.10 | 60.92 | 148.02 | 31.39 | 289.17 |
| envio_evaluacion_transaccion | 200 | 52.47 | 43.91 | 139.81 | 34.08 | 148.60 |
| login_bcrypt | 200 | 435.15 | 412.52 | 550.00 | 379.12 | 754.28 |
| login_consulta_usuario | 200 | 122.21 | 98.86 | 207.00 | 66.85 | 286.73 |
| login_total | 200 | 557.36 | 539.24 | 716.72 | 451.09 | 891.77 |
| select_1 | 200 | 0.23 | 0.21 | 0.34 | 0.17 | 1.08 |

Se pedían 1 000 repeticiones; se hicieron **200** por falta de tiempo. No se hizo la comparación local frente a red (un solo equipo).
Lectura: el cuello de botella del inicio de sesión no es la red ni la base de datos sino **BCrypt (mediana 412 ms de los 539 ms)**;
la transacción de envío de la evaluación (mediana 44 ms) y la conexión JDBC (mediana 61 ms) están muy por debajo de 0,1 s y 1 s.

## T03 · Concurrencia cliente-servidor (5 rondas por cliente: inicio de sesión → consulta de práctica → inicio de intento → envío)

| Clientes | Operaciones | Errores | Duración (s) | Operaciones/s | Conexiones activas máx. | Operación | p50 (ms) | p95 (ms) |
|---|---|---|---|---|---|---|---|---|
| 1 | 20 | 0 | 3.40 | 5.88 | 2 | inicio_sesion | 500.28 | 521.78 |
| 1 | 20 | 0 | 3.40 | 5.88 | 2 | consulta_practica | 43.90 | 136.88 |
| 1 | 20 | 0 | 3.40 | 5.88 | 2 | inicio_intento | 52.67 | 59.69 |
| 1 | 20 | 0 | 3.40 | 5.88 | 2 | envio_evaluacion | 49.57 | 63.49 |
| 5 | 100 | 0 | 6.43 | 15.55 | 2 | inicio_sesion | 926.17 | 1131.25 |
| 5 | 100 | 0 | 6.43 | 15.55 | 2 | consulta_practica | 82.17 | 162.89 |
| 5 | 100 | 0 | 6.43 | 15.55 | 2 | inicio_intento | 85.58 | 151.69 |
| 5 | 100 | 0 | 6.43 | 15.55 | 2 | envio_evaluacion | 90.62 | 109.89 |
| 10 | 200 | 0 | 14.05 | 14.23 | 9 | inicio_sesion | 1771.28 | 2915.29 |
| 10 | 200 | 0 | 14.05 | 14.23 | 9 | consulta_practica | 233.94 | 568.29 |
| 10 | 200 | 0 | 14.05 | 14.23 | 9 | inicio_intento | 202.93 | 486.94 |
| 10 | 200 | 0 | 14.05 | 14.23 | 9 | envio_evaluacion | 250.36 | 464.10 |
| 30 | 600 | 0 | 33.71 | 17.80 | 25 | inicio_sesion | 4797.84 | 5383.61 |
| 30 | 600 | 0 | 33.71 | 17.80 | 25 | consulta_practica | 650.83 | 1011.82 |
| 30 | 600 | 0 | 33.71 | 17.80 | 25 | inicio_intento | 541.20 | 893.44 |
| 30 | 600 | 0 | 33.71 | 17.80 | 25 | envio_evaluacion | 583.13 | 1021.03 |

0 errores en todos los niveles. Las conexiones activas máximas (2, 2, 9 y 25) salen de `pg_stat_activity`. El inicio de sesión
crece casi linealmente con los clientes (mediana de 0,50 s con 1 cliente a 4,8 s con 30) porque BCrypt es CPU y la máquina tiene
2 núcleos: **es una limitación de CPU del cliente simulado, no de la base de datos**. En la sala real cada estudiante tendría su
propio equipo y no competirían por la CPU. La prueba usa hilos en una sola JVM, así que subestima la escala real.

## T04–T10 · No ejecutadas

| ID | Estado | Motivo |
|---|---|---|
| T04 Distribución del modelo 3D | No ejecutada | Faltó tiempo. Además no hay `clumsy`/`tc`; habría que emular el ancho de banda en el servidor. Se instaló `gltf-transform` (Draco/meshopt) en `build/` pero no se usó |
| T05 Costo de la compresión | No ejecutada | Depende de T04 |
| T06 Canal de eventos visor-aplicación | No ejecutada | El canal está probado funcionalmente (C05: `ExplorationIntegrationTest`), pero no se midieron bytes, latencia ni 30 visores |
| T07 Captura de tráfico | No ejecutada | No hay `tshark`/Wireshark; el tráfico de bucle local no lo captura `pktmon` |
| T08 Cifrado del enlace | No ejecutada | Requiere un clúster PostgreSQL con `ssl=on`; no se modificó el servidor real. El servidor actual tiene `ssl=off` |
| T09 Exposición y endurecimiento | No ejecutada | Dato ya observado: el servidor tiene `listen_addresses='*'` (escucha en todas las interfaces) y `ssl=off`; no se verificó `pg_hba.conf`. El rol de solo lectura sí está probado (C09) |
| T10 Calidad de servicio percibida | Parcial | Con los datos de T02/T03: la conexión (≈0,06 s), la consulta (≈0,04 s) y el envío (≈0,04 s) caben bajo 0,1 s; el inicio de sesión (≈0,5 s) y el primer cuadro del visor (≈3,5 s) caben bajo 1 s y 5 s respectivamente. Umbral propuesto, a validar en la sala: operaciones de datos ≤ 0,1 s p95 con ≤ 10 clientes; inicio de sesión ≤ 1 s p95 con ≤ 5 clientes simultáneos por CPU; visor ≤ 5 s hasta el primer cuadro |
