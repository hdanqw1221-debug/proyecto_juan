# Sistema de Gestión Académica y Simulador Anatómico Equino 3D
### Universidad de Pamplona

Plataforma integral de gestión del aprendizaje (LMS) y simulación tridimensional para la enseñanza de medicina veterinaria y zootecnia, desarrollada con **Java 21 (Swing + JDBC)**, persistencia relacional en **PostgreSQL**, y visores interactivos tridimensionales en **Python (PyQt5 + VTK)**.

---

## 📚 Documentación Oficial del Proyecto

La documentación exhaustiva del proyecto está organizada en la carpeta [`docs/`](docs/):

- 📖 **[Índice General y Guía de Navegación](docs/README.md)**: Resumen institucional y mapa documental.
- ⚙️ **[Manual Técnico y de Arquitectura](docs/MANUAL_TECNICO.md)**: Diagrama de arquitectura, patrones de diseño (DAO, Service Layer, Factory, SessionManager), protocolo IPC Java-Python con stdout streaming JSON, motor de despiece progresivo y suite de pruebas JUnit 5.
- 🗄️ **[Manual de Base de Datos y Diccionario de Datos](docs/BASE_DE_DATOS.md)**: Diagrama Entidad-Relación (16 tablas), normalización 3FN, diccionario detallado campo por campo, especificación de columnas JSONB y respaldos.
- 👥 **[Manual de Usuario y Operación Funcional](docs/MANUAL_USUARIO.md)**: Guía ilustrada para Administrador (usuarios, importación CSV, cursos, auditoría), Profesor (exámenes, banco de preguntas con imágenes, prácticas 3D, notas y CSV), Estudiante (evaluaciones, certificados con hash SHA-256) y manejo de los 4 visores 3D.
- 🚀 **[Guía de Despliegue, Instalación y Empaquetado](docs/GUIA_DESPLIEGUE.md)**: Requisitos del sistema, instalador todo-en-uno EXE para Windows, configuración del entorno de desarrollo (JDK, PostgreSQL, Python .venv), compilación de producción con `run.ps1 -Mode package` y solución de incidencias.

---

## 🚀 Arquitectura y Tecnologías

- **Capa de Presentación (Java Desktop)**: Java Swing con tema moderno (`UiTheme`), diálogos interactivos, componentes visuales para fechas/horas (`DateTimePicker`), selector de imágenes para cuestionarios, y visor de bitácora/auditoría.
- **Capa de Persistencia y Negocio**: JDBC estructurado con arquitectura DAO, pools de conexión seguros, transacciones atómicas, validación de configuraciones JSON y hashing de contraseñas con **jBCrypt**.
- **Motor 3D Interactivo**: visores de las cavidades torácica, abdominal e inguinal del equino con **VTK** y **PyQt5**: despiece inmersivo al acercarse, zoom hacia el cursor, selección por clic y ficha anatómica completa de cada estructura (desde PostgreSQL).
- **Base de Datos**: PostgreSQL en 3FN con claves foráneas, restricciones de integridad referencial, índices de optimización y columnas JSONB para configuraciones pedagógicas flexibles y auditoría institucional.

---

## 👥 Roles del Sistema y Permisos

El sistema implementa separación estricta de responsabilidades mediante control de acceso por roles:

| Rol | Alcance y Funcionalidades Principales |
| :--- | :--- |
| **`ADMINISTRADOR`** | • Gestión completa de usuarios (creación, edición, activación/inactivación, asignación múltiple de roles).<br>• Gestión completa de materias (creación, edición, eliminación y asignación del docente titular).<br>• Asignación y desasignación de estudiantes a materias.<br>• Acceso a bitácora de auditoría institucional en tiempo real.<br>• Métricas globales y control del sistema. |
| **`PROFESOR`** | • Gestión académica de materias asignadas.<br>• Creación y edición de evaluaciones con límite de intentos configurables y tiempo límite en minutos.<br>• Banco de preguntas con soporte para imágenes médicas y puntajes ponderados.<br>• Creación, edición, eliminación y **previsualización directa de modelos 3D** desde el panel docente.<br>• Calificaciones, exportación segura a CSV (RFC 4180 / UTF-8 BOM) y **Revisión Detallada de Respuestas** por intento. |
| **`ESTUDIANTE`** | • Portal académico personalizado con listado de materias inscritas y nombre del docente titular.<br>• Presentación de exámenes en tiempo real con cronómetro regresivo, paginación y navegación ágil.<br>• Historial de intentos con desglose pregunta por pregunta y retroalimentación inmediata.<br>• Ejecución de prácticas guiadas y apertura directa de los **Modelos Anatómicos 3D**. |

---

## 🔑 Credenciales de Prueba

Para facilitar pruebas y demostraciones inmediatas, puedes inicializar los usuarios predefinidos ejecutando [`db/test_users.sql`](db/test_users.sql):

| Usuario | Contraseña | Rol Principal | Descripción |
| :--- | :--- | :--- | :--- |
| `admin` | `admin123` | **ADMINISTRADOR** | Control total de usuarios, roles y auditoría |
| `profesor` | `profesor123` | **PROFESOR** | Gestión de exámenes, banco de preguntas y simulaciones 3D |
| `juan` | `juan123` | **ESTUDIANTE** | Alumno matriculado en Anatomía Veterinaria |
| `maria` | `maria123` | **ESTUDIANTE** | Alumna matriculada en Anatomía Veterinaria |

---

## 🐴 Catálogo de Modelos 3D Interactivas

Ubicados en `aplicacion_tres_d/aplicacion_tres_d/`:

1. **Cavidad torácica (`cavidad_toracica.py`)**: modelo inmersivo. Desde lejos se ve la cavidad completa; al acercarse con la rueda del ratón se separa en pulmones, corazón, pericardio, tráquea, esófago, grandes vasos, mediastino y diafragma, con etiquetas. El clic en cualquier pieza muestra su ficha completa.
2. **Cavidad abdominal (`cavidad_abdominal.py`)**: modelo inmersivo del abdomen y la transición pélvica (tubo digestivo, hígado, páncreas, bazo, riñones, adrenales, uréteres, grandes vasos, recto y vejiga).
3. **Región inguinal (`region_inguinal.py`)**: modelo inmersivo de la pared inguinal y el escroto (anillos, conducto inguinal, cordón espermático, conducto deferente, vasos testiculares, testículo y epidídimo).
4. **Visor general de cavidades (`cavidades_equinas.py`)**: las 58 estructuras a la vez, con conmutadores por cavidad, selección por clic (y clic repetido para llegar a estructuras profundas) y ficha anatómica.

Todos se generan en Blender por script reproducible y leen las fichas desde PostgreSQL o JSON.

**Integración con la aplicación Java** (`src/app/service/Visor3DService.java`, común a los paneles del estudiante, el docente y el administrador):

- **Catálogo único** de los cuatro visores, con su título, descripción y plantilla de práctica. La carpeta se localiza por la escena compartida (`cavidades/modelos/manifiesto.json`), tanto en el proyecto como en la aplicación empaquetada.
- **Python**: primero el entorno `.venv` del proyecto o el runtime embebido del paquete, después el del sistema. Los visores se abren con `pythonw.exe`, sin ventana de consola.
- **Sesión auditada**: el visor informa por la salida estándar de su apertura, de cada estructura seleccionada y de su cierre. Al cerrarlo se registra `SIM_3D_SESION` con las fichas consultadas y la duración.
- **Prácticas**: el botón **«+ Practicas de cavidades 3D»** del panel docente crea en el curso una práctica por cada modelo que aún no tenga. Al crear una simulación, elegir el modelo carga su plantilla (objetivos, instrucciones y escenario propios). Documentación en [`cavidades/docs/MODELOS_README.md`](aplicacion_tres_d/aplicacion_tres_d/cavidades/docs/MODELOS_README.md); esquema en `db/cavidades_schema.sql` y datos en `db/cavidades_fichas.sql`.

---

## 📦 Instalador para Windows (todo incluido)

El instalador `GestionAcademica-<versión>.exe` no necesita nada instalado en el equipo destino: incluye **Java** (runtime de `jpackage`), **Python 3.14** con PyQt5, VTK y numpy (`python-runtime`) y **PostgreSQL 18** (`pgsql`, con el runtime de Visual C++ que necesita).

- **Base de datos embebida** (`app.db.embedded=true` en `app/config/app.properties` del paquete): al abrir la aplicación por primera vez se crea la base en `%LOCALAPPDATA%\GestionAcademica\basedatos` con el esquema de `db/` y los usuarios de prueba (`app.db.embedded.demoData`). Tarda unos segundos. Después se arranca y se detiene con la aplicación, escucha solo en `127.0.0.1` (puerto 15432 o el siguiente libre) y el superusuario tiene una clave aleatoria guardada en esa carpeta. Los datos se conservan al desinstalar.
- **Servidor externo**: con `app.db.embedded=false` la aplicación usa la conexión `app.db.url`, `app.db.user` y `app.db.password`, como en desarrollo.
- Los respaldos del panel de administración usan los `pg_dump`/`pg_restore` del paquete.

**Generar el instalador** (en el equipo de desarrollo, que sí necesita el JDK de `oracleJdk-26/`, WiX Toolset 3.14, `build/python-runtime` y una instalación de PostgreSQL de la que copiar los binarios):

```powershell
.\run.ps1 -Mode package                      # versión por defecto (1.1.0)
.\run.ps1 -Mode package -Version 1.1.1 -PostgresSource "C:\Program Files\PostgreSQL\18"
```

El PostgreSQL portable se prepara una vez en `build/pgsql-runtime` (sin pgAdmin, StackBuilder ni documentación); bórralo para volver a copiarlo. El instalador queda en `build/dist/`.

---

## 🛠️ Instalación y Requisitos (desarrollo)

### 1. Prerrequisitos de Software
- **Java Development Kit (JDK 21 o superior)**: Se incluye soporte para JDK 26 local en `oracleJdk-26/` o cualquier JDK 21+ configurado en el `PATH`.
- **PostgreSQL 14 o superior**: En ejecución en `localhost:5432` con una base de datos creada (por defecto llamada `gestion` o según tu configuración).
- **Python 3.9 o superior** (probado con 3.14) para los modelos 3D:
  - Las dependencias se encuentran en `aplicacion_tres_d/aplicacion_tres_d/requeriments.txt` (`PyQt5`, `vtk`, `numpy`, `psycopg`).
  - La aplicación usa el entorno virtual `aplicacion_tres_d/aplicacion_tres_d/.venv/`; si no existe, el panel del estudiante lo crea e instala las dependencias al abrir el primer modelo.
  - `psycopg` es opcional: sin él, los visores leen las fichas desde `cavidades/datos/fichas_anatomicas.json`.

#### Versiones probadas (las más recientes de VTK y PyQt5 pueden fallar)

| Componente | Versión probada | Notas |
|---|---|---|
| Java | JDK 21 (compilar con `javac --release 21`) | probado con Temurin 25 compilando para 21 |
| PostgreSQL | 18.3 | el esquema funciona desde 14 |
| Python | 3.14.4 | con Python < 3.14 usar `vtk==9.4.1` (ver `requeriments.txt`) |
| PyQt5 | **5.15.11** (Qt 5.15.2) | exacta |
| VTK | **9.6.2** (Python 3.14) / 9.4.1 (Python < 3.14) | `>=9.6.2,<10` en Python 3.14 |
| numpy | 2.5.1 | `>=1.24` |
| psycopg | 3.3.6 | opcional; `>=3.2` |
| Gson / PostgreSQL JDBC / jBCrypt | 2.11.0 / 42.7.10 / 0.4 | en `lib/` |

Al abrir un visor, la aplicación comprueba las versiones de PyQt5 y VTK del Python elegido contra
`requeriments.txt` y, si no coinciden, muestra qué versión hay, cuál se probó y cómo instalarla
(`python -m pip install -r requeriments.txt`), en lugar de abrir un visor que puede fallar.

### 2. Configuración de Base de Datos
Copia [`config/app.properties.example`](config/app.properties.example) a `config/app.properties` y edita este último con las credenciales de tu servidor PostgreSQL:

```properties
app.db.url=jdbc:postgresql://localhost:5432/gestion
app.db.user=postgres
app.db.password=tu_password_aqui
```

**La contraseña no debe guardarse en el repositorio.** `config/app.properties` está en `.gitignore`. Se recomienda
definirla en la variable de entorno `APP_DB_PASSWORD` (tiene prioridad sobre el archivo; también `APP_DB_URL` y
`APP_DB_USER`):

```powershell
$env:APP_DB_PASSWORD = "tu_password_aqui"
```

Luego, inicializa la estructura ejecutando los scripts SQL en tu cliente PostgreSQL (pgAdmin, psql, DBeaver):
```bash
# 1. Crear las tablas e índices
psql -U postgres -d gestion -f db/schema.sql

# 2. Catálogo anatómico (cavidades, estructuras y modelos 3D) y sus fichas
psql -U postgres -d gestion -f db/cavidades_schema.sql
psql -U postgres -d gestion -f db/cavidades_fichas.sql

# 3. Exploración del visor (exploracion_eventos) y enlace pregunta-estructura 3D (preguntas.objeto_id)
psql -U postgres -d gestion -f db/validacion_schema.sql

# 4. Cargar usuarios, roles, curso de muestra y simulaciones 3D
psql -U postgres -d gestion -f db/test_users.sql

# 5. (Recomendado) Rol de solo lectura para el visor 3D
psql -U postgres -d gestion -f db/rol_visor_lectura.sql
psql -U postgres -d gestion -c "ALTER ROLE visor_lectura PASSWORD '<clave nueva>'"
```

El visor lee las fichas con `CAVIDADES_DB_DSN` si existe; con el rol de solo lectura no puede modificar la base:

```powershell
$env:CAVIDADES_DB_DSN = "host=localhost port=5432 dbname=gestion user=visor_lectura password=<clave nueva>"
```

---

## ▶️ Ejecución del Proyecto

### Opción 1: Script Automatizado (Recomendada en Windows)
Puedes iniciar la aplicación con un solo clic o por comando:
- **Doble clic o CMD:** Ejecuta el archivo [`run.bat`](run.bat) (o escribe `.\run.bat`).
- **PowerShell:** Ejecuta `.\run.ps1`.

El script detecta automáticamente el compilador Java (`oracleJdk-26` o del sistema), compila las clases en `out/`, valida las librerías en `lib/` e inicia la ventana principal.

### Opción 2: Compilación y Ejecución Manual

**En Windows (PowerShell / CMD):**
```powershell
# Compilación
javac -encoding UTF-8 -cp "lib/*" -d out (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName })

# Ejecución
java -cp "out;lib/*" app.Main
```

**En Linux / macOS:**
```bash
# Compilación
javac -encoding UTF-8 -cp "lib/*" -d out $(find src -name "*.java")

# Ejecución
java -cp "out:lib/*" app.Main
```

---

## 💡 Herramienta de Generación de Contraseñas

Si deseas generar nuevas contraseñas con hash BCrypt:
```powershell
java -cp "out;lib/*" app.tools.HashTool nueva_clave_123
```
Copia el hash generado e insértalo directamente en la tabla `usuarios`.
