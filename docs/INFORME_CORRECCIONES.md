# Informe de correcciones (rama `correcciones-validacion`)

Proyecto: «Simulador 3D de las cavidades torácica, abdominal e inguinal del equino usando Python, Java y Blender».
Base: confirmación `8518bc9` (10 de noviembre de 2026). Fecha de las correcciones: 18 de noviembre de 2026.

## Entorno de verificación

| Elemento | Valor |
|---|---|
| Sistema operativo | Windows 10 Enterprise LTSC 2019 (10.0.17763) |
| Procesador | Intel Core i7-4600U, 2 núcleos, 2,10 GHz |
| Gráficos | Intel HD Graphics Family (integrada, controlador 20.19.15.5070). **Sin tarjeta gráfica dedicada.** |
| Java | Temurin 25.0.3, compilando con `javac --release 21` |
| Python | 3.14.4 (entorno `.venv`): VTK 9.6.2, PyQt5 5.15.11 (Qt 5.15.2), numpy 2.5.1, psycopg 3.3.6, pytest 9.1.1 |
| PostgreSQL | 18.3 local. Las pruebas usan la base aislada `gestion_pruebas`, creada solo con los scripts de `db/`; la base `gestion` no se modificó. |

Línea base antes de cualquier cambio (`evidencias/2026-11-18/linea_base/`): **JUnit 33/33** y **pytest 56/56** aprobadas.

## Bloque 0. Seguridad del repositorio

| ID | Hallazgo | Archivos | Verificación | Resultado |
|---|---|---|---|---|
| S01 | `config/app.properties` versionado con URL, usuario y contraseña reales | `.gitignore`, `config/app.properties.example` | `git ls-files config` solo lista el `.example` | Corregido (deja de versionarse; el archivo local se conserva) |
| S02 | `scratch/` versionado pese a `.gitignore`, con usuario `postgres` y su contraseña en texto plano | `.gitignore` | `git ls-files scratch` vacío | Corregido |
| S03 | `out/` con 183 `.class` de bytecode mezclado (Java 21, 25 y 26) | `.gitignore` (`out/`, `*.class`) | `git ls-files out` vacío | Corregido |
| S04 | La contraseña solo se podía dar en el archivo | `AppConfig.java`, `repositorio_fichas.py` | `AppConfigTest` (5), `test_configuracion_bd.py` (4) | Corregido: `APP_DB_PASSWORD` (y `APP_DB_URL`, `APP_DB_USER`) tienen prioridad sobre el archivo |
| S05 | Un clon limpio no tendría `config/app.properties` y `EmbeddedDatabaseTest` fallaría | `EmbeddedDatabaseTest.java` | JUnit | Corregido: usa la plantilla `.example` |

La contraseña de PostgreSQL aparece en **6 confirmaciones** del historial público y además coincide con el nombre de uno de los usuarios de prueba (palabra de 4 letras). Acciones pendientes del autor: ver la sección «Acciones del autor».

## Bloque 1. Correcciones

| ID | Hallazgo de origen | Archivos modificados | Prueba que lo verifica | Resultado |
|---|---|---|---|---|
| C01 | PF-28 y experto en software: «Actividad reciente» mostraba eventos de otros usuarios y roles | `LogDao.java`, `LogDaoJdbc.java` (`listRecentByUser`), `AuditService.java` (`listRecentFor`), `DashboardPanel.java` | `LogDaoJdbcIntegrationTest.cadaUsuarioSoloRecibeSusEventos` (PostgreSQL real, dos usuarios), `AuditServiceTest` | Corregida. El administrador ve todo; profesor y estudiante solo lo propio. Evidencia: `evidencias/2026-11-18/C01/` |
| C02 | PF-27: enviar la evaluación no generaba evento de auditoría | `ExamAttemptService.java` (`submitAttempt`), `ExamDialog.java` | `ExamSubmitIntegrationTest` (3 casos: evento con examen, intento y calificación; reversión total si falla el guardado; otro usuario no puede enviar el intento) | Corregida. Además, el cierre del intento, el detalle en `resultados` y el evento `EXAM_SUBMIT` ahora van en **una sola transacción** (antes eran dos escrituras independientes) y un intento no se puede reenviar. Evidencia: `evidencias/2026-11-18/C02/` |
| C03 | Experto en software: en la pantalla de evaluación del estudiante se podía editar el nombre de la evaluación | `ReadOnlyTableModel.java` (nuevo, `isCellEditable` → `false`, `final`), `AdminPanel.java` (5 modelos), `StudentPanel.java` (4), `TeacherPanel.java` (5) | `ReadOnlyTableModelTest` (ninguna celda editable; `JTable.editCellAt` rechazado; revisión automática de `src/app/ui` que falla si reaparece un `new DefaultTableModel(`) | Corregida. Revisados los 14 modelos de tabla de `ui/`: ninguno dependía de la edición en celda (todo se edita con formularios). Evidencia: `evidencias/2026-11-18/C03/` |
