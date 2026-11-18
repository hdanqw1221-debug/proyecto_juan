# Informe de pruebas internas (regresión)

Fecha: 18 de noviembre de 2026 · Rama `correcciones-validacion` · Evidencia: `evidencias/2026-11-18/`.
Todas las cifras salen de ejecuciones hechas en esta sesión; lo que no se pudo repetir figura como **No repetida** con su motivo.

## 1. Entorno

| Elemento | Valor |
|---|---|
| Procesador | Intel Core i7-4600U, 2 núcleos / 4 hilos, 2,10 GHz |
| Gráficos | Intel HD Graphics Family (integrada), controlador 20.19.15.5070. **No hay tarjeta gráfica dedicada** (el prompt suponía una) |
| Sistema operativo | Windows 10 Enterprise LTSC 2019 (10.0.17763) |
| Java | Temurin 25.0.3 y JDK 26 local; compilación con `javac --release 21` (bytecode versión 65) |
| Python / VTK / PyQt5 / numpy | 3.14.4 / 9.6.2 / 5.15.11 (Qt 5.15.2) / 2.5.1 |
| PostgreSQL | 18.3; pruebas sobre `gestion_pruebas` y `gestion_pf` (la base `gestion` no se modificó) |

## 2. Regresión automática

| Suite | Antes (línea base `8518bc9`) | Ahora | Fallidas |
|---|---|---|---|
| JUnit 5 (`--release 21`) | 33 | 114 | 0 |
| pytest (`cavidades/tests`) | 56 | 71 | 0 |

Salidas crudas: `linea_base/` y `final/`. Ninguna prueba que pasaba antes dejó de pasar.

## 3. Rendimiento del visor (5 repeticiones por modelo, arranque en frío)

Medido con `cavidades/herramientas/medir_rendimiento.py` (`bloque2/rendimiento_visor.csv`). «Carga» = desde que arranca el proceso del
visor hasta el primer cuadro renderizado (incluye importar VTK y Qt). Giro: 72 pasos de 5° con un `Render` cada uno. Selección:
20 clics en posiciones aleatorias del centro de la ventana (mediana; incluye el clic y el nuevo renderizado).

| Modelo | n | Carga hasta el primer cuadro (s), media (mín–máx) | fps en giro de 360°, media (mín–máx) | Mediana de latencia de selección (ms) | Objetos | Fichas desde |
|---|---|---|---|---|---|---|
| Torácica | 5 | 3,46 (3,12–4,29) | 21,7 (20,2–22,8) | 107,3 | 12 | postgresql |
| Abdominal | 5 | 3,76 (3,40–4,52) | 38,5 (35,6–39,8) | 76,0 | 29 | postgresql |
| Inguinal | 5 | 3,55 (3,27–3,69) | 28,1 (26,5–30,5) | 43,3 | 17 | postgresql |
| General | 5 | 5,32 (4,88–5,94) | 30,0 (28,1–31,3) | 47,8 | 61 | postgresql |

Comparación con la tesis (PF-29, otro equipo): carga 0,54–1,32 s, 3,1–6,3 fps, selección ≤ 0,4 ms. **No son comparables**: la tesis mide
la construcción de la escena (sin importar VTK/Qt), este equipo no tiene GPU dedicada, y mi latencia de selección incluye el
renderizado posterior, no solo el *picking*. Mediciones sin umbral.

## 4. Matriz PF-01 a PF-40 (matriz de la tesis, `docs/entrada/matriz_PF_tesis.tsv`)

Estados: **Aprobada**, **Aprobada con observación**, **Fallida**, **Medición sin umbral**, **No repetida** (no se pudo ejecutar aquí).

| ID | Prueba | Resultado obtenido en esta sesión | Estado | Evidencia |
|---|---|---|---|---|
| PF-01 | Compilación desde fuentes | `scripts/compilar.ps1 -IncluirPruebas`: 121 fuentes (src y tests), 209 clases, todas versión 65 (Java 21), sin errores | Aprobada | C12/compilacion_limpia.txt |
| PF-02 | Pruebas unitarias | JUnit: 114 aprobadas, 0 fallidas. La prueba de `pg_dump` (defecto de la tesis) pasa | Aprobada | final/ |
| PF-03 | Esquema académico | 12 tablas, 13 claves foráneas, 29 índices | Aprobada | bloque2/PF03_04_12.txt |
| PF-04 | Datos de prueba | 4 usuarios, 3 roles, 1 materia, 2 matrículas; **3** prácticas 3D (no 4, ver PF-13) | Aprobada con observación | bloque2/PF03_04_12.txt |
| PF-05 | Credenciales inválidas | El evento `LOGIN_FAIL` lo escribe `LoginFrame` (interfaz) | No repetida (requiere interfaz) | — |
| PF-06 | Hash BCrypt | Hashes `$2a$12$`, longitud 60 (en la base cargada) | Aprobada (solo hash) | bloque2/PF03_04_12.txt |
| PF-07 | Menú según el rol | Requiere sesiones en la interfaz | No repetida | — |
| PF-08 | Control de acceso en código | `ServiceAuthorizationTest`: estudiante, sin sesión y profesor reciben `SecurityException` en 25 operaciones | Aprobada (ampliada por C04) | C04/ |
| PF-09 | Creación de cuestionario | Requiere interfaz (el servicio de creación solo se prueba indirectamente) | No repetida | — |
| PF-10 | Banco de preguntas por lote | `QuestionObjectLinkIntegrationTest`: creación y edición por lote persistidas | Aprobada (vía servicio) | C07/ |
| PF-11 | Atomicidad del lote | Un lote con una pregunta inválida (estructura inexistente) no deja preguntas a medias | Aprobada | C07/ |
| PF-12 | Integridad referencial | Pregunta para el examen 9999 → SQLSTATE 23503 (`fk_preguntas_examen`) | Aprobada | bloque2/PF03_04_12.txt |
| PF-13 | Prácticas 3D | `test_users.sql` crea **3** prácticas y no las 4 que espera la tesis (falta la «general»); `SimulationService.addCavityPractices` sí crea las 4 | Aprobada con observación | bloque2/ |
| PF-14 | Práctica vista por el estudiante | Requiere interfaz | No repetida | — |
| PF-15 | Apertura del visor desde Java | Requiere interfaz; proceso hijo y eventos cubiertos por `Visor3DServiceTest` y C05 | No repetida (parcial por pruebas) | — |
| PF-16 | Carga del modelo general | 61 objetos, fichas desde postgresql; primer cuadro 5,3 s medio | Aprobada | bloque2/rendimiento_visor.csv |
| PF-17 | Rotación y acercamiento | 80 ensayos acercar-girar-acercar sin errores (programáticos, no con ratón) | Aprobada | C08/ensayos_codigo_nuevo.txt |
| PF-18 | Identidad de la estructura | `test_seleccion_en_el_centro_de_un_organo` (6 estructuras) y `test_clic_en_el_fondo_no_selecciona` | Aprobada | final/pytest |
| PF-19 | Resaltado | Requiere inspección visual | No repetida | — |
| PF-20 | Rotación sin selección | Requiere arrastre real | No repetida | — |
| PF-21 | Cierre normal | Requiere cerrar la ventana real | No repetida | — |
| PF-22 | Terminación anormal | `VisorDiagnosticoTest`: el mensaje distingue 6 causas y ya no culpa siempre a los gráficos (C13) | Aprobada | C13/ |
| PF-23 | Presentación de la evaluación | Requiere interfaz | No repetida | — |
| PF-24 | Calificación | El cálculo vive en `ExamDialog` (interfaz) | No repetida | — |
| PF-25 | Revisión con retroalimentación | Requiere interfaz | No repetida | — |
| PF-26 | Seguimiento docente | Requiere interfaz; `studentNames` probado (C14) | No repetida | — |
| PF-27 | Trazabilidad | El envío de la evaluación ahora genera `EXAM_SUBMIT` (C02) | Aprobada (observación resuelta) | C02/ |
| PF-28 | Privacidad de la auditoría | Cada usuario solo recibe sus eventos (C01) | Aprobada (defecto corregido) | C01/ |
| PF-29 | Rendimiento del visor | Ver sección 3 | Medición sin umbral | bloque2/ |
| PF-30 | Suite del modelo | pytest 71 aprobadas con VTK 9.6.2 (no se probó con 9.4.1 ni 9.7.1) | Aprobada | final/ |
| PF-31 | Identidad de todas las estructuras | Manifiesto: 58 seleccionables de 61; pytest prueba 6 representativas, **no las 58 una por una** | Aprobada con observación | — |
| PF-32 | Alcance por clic repetido | No repetida | No repetida | — |
| PF-33 | Cobertura de las áreas | Manifiesto: 12 torácicas, 27 abdominales, 2 pélvicas, 17 inguinales, 3 de referencia/envoltura (61 en total) | Aprobada con observación | — |
| PF-34 | Consistencia con Blender | No repetida (no se abrió el `.blend`) | No repetida | — |
| PF-35 | Fichas PostgreSQL y JSON | 47 fichas desde postgresql con el rol de solo lectura (C09) y `test_repositorio_bd_coincide_con_json` | Aprobada | C09/ |
| PF-36 | Despiece torácico e inguinal | 80 ensayos (torácica e inguinal: 40 del código original y 40 del nuevo) sin cierres | Aprobada | C08/ |
| PF-37 | Estabilidad del modelo abdominal | **No reproducido**: 0 cierres en 80 ensayos del abdominal (60 del código original en tres configuraciones y 20 del nuevo) | No concluyente (ver C08) | C08/RESUMEN_C08.md |
| PF-38 | Eventos del visor | La aplicación ahora los procesa y guarda (C05); el arrastre no genera evento | Aprobada (observación resuelta) | C05/ |
| PF-39 | Regeneración desde el código | No repetida (C15 la ejecuta y no terminó) | No repetida | — |
| PF-40 | Compresión para distribución | T04/T05 no se ejecutaron | No repetida | — |

## 5. Casos nuevos para C01–C16

| Caso | Prueba que lo cubre | Resultado |
|---|---|---|
| C01 | `LogDaoJdbcIntegrationTest`, `AuditServiceTest` | Aprobada |
| C02 | `ExamSubmitIntegrationTest` (3) | Aprobada |
| C03 | `ReadOnlyTableModelTest` (2) | Aprobada |
| C04 | `ServiceAuthorizationTest` (4) | Aprobada |
| C05 | `VisorEventTest`, `ExplorationIntegrationTest` (3) | Aprobada |
| C06 | `SimulationConfigValidationTest` (13) | Aprobada |
| C07 | `QuestionObjectLinkIntegrationTest` (5) | Aprobada |
| C08 | `test_diagnostico_cierres.py` (9) + 80 ensayos | Aprobada; fallo original **no reproducido** |
| C09 | `test_rol_solo_lectura.py` (2) | Aprobada |
| C10 | `DependencyCheckTest` (7) | Aprobada |
| C11 | Revisión de archivos | Aprobada |
| C12 | `scripts/compilar.ps1` (209 clases, versión 65) | Aprobada |
| C13 | `VisorDiagnosticoTest` (10) | Aprobada |
| C14 | `FormatsTest`, `TableLayoutTest`, `StudentNamesIntegrationTest` + capturas | Aprobada (sin prueba con docentes) |
| C15 | Trabajo parcial en `pj_anatomia`, sin validar | **Pendiente** |
| C16 | Ídem | **Pendiente** |

## 6. Trabajo futuro (fuera de esta sesión)

Representación de los genitales de la hembra y de la glándula mamaria (no implementada, por instrucción).
