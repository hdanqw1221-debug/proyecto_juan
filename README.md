# Sistema de Gestión Académica y Simulador Anatómico Equino 3D
### Universidad de Pamplona

Plataforma integral de gestión del aprendizaje (LMS) y simulación tridimensional para la enseñanza de medicina veterinaria y zootecnia, desarrollada con **Java 21 (Swing + JDBC)**, persistencia relacional en **PostgreSQL**, y visores interactivos tridimensionales en **Python (PyQt5 + VTK)**.

---

## 🚀 Arquitectura y Tecnologías

- **Capa de Presentación (Java Desktop)**: Java Swing con tema moderno (`UiTheme`), diálogos interactivos, componentes visuales para fechas/horas (`DateTimePicker`), selector de imágenes para cuestionarios, y visor de bitácora/auditoría.
- **Capa de Persistencia y Negocio**: JDBC estructurado con arquitectura DAO, pools de conexión seguros, transacciones atómicas, validación de configuraciones JSON y hashing de contraseñas con **jBCrypt**.
- **Motor 3D Interactivo**: Visualizador de modelos GLTF/GLB acelerado por hardware con **VTK** y renderizado interactivo en **PyQt5**. Permite rotación orbital 360°, zoom, oscurecimiento/resaltado de selección y visualización contextual de nombres y descripciones anatómicas veterinarias reales.
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

1. **Piel del Caballo (`piel_caballo.py`)**: Estudio del sistema tegumentario equino, capas dérmicas y funciones homeostáticas y protectoras.
2. **Sistema Cardiovascular (`sistema_cardiovascular.py`)**: Visualización detallada de aurículas, ventrículos, miocardio, válvulas cardíacas, aorta, tronco pulmonar, vena yugular y plexos vasculares periféricos del casco.
3. **Sistema Óseo (`sistema_oseo.py`)**: Esqueleto axial y apendicular equino (cráneo, columna vertebral, costillas, escápula, caña, cuartilla, tejuelo y hueso navicular).
4. **Sistema Muscular (`sistema_muscular.py`)**: Grupos musculares superficiales y profundos (masetero, braquiocefálico, dorsal ancho, glúteos, aparato recíproco y tendones flexores).
5. **Órganos Internos (`sistema_organosinternos.py`)**: Aparato digestivo completo (estómago con margo plicatus, ciego de gran volumen, flexura pélvica, colon mayor y menor), hígado sin vesícula, bazo, tráquea, pulmones y riñones acorazonados.
6. **Cavidades torácica, abdominal e inguinal (`cavidades_equinas.py`)**: 58 estructuras independientes generadas en Blender por script reproducible, con selección por clic (y clic repetido para llegar a estructuras profundas) y ficha anatómica desde PostgreSQL o JSON. Documentación en [`cavidades/docs/MODELOS_README.md`](aplicacion_tres_d/aplicacion_tres_d/cavidades/docs/MODELOS_README.md); esquema en `db/cavidades_schema.sql` y datos en `db/cavidades_fichas.sql`.

---

## 🛠️ Instalación y Requisitos

### 1. Prerrequisitos de Software
- **Java Development Kit (JDK 21 o superior)**: Se incluye soporte para JDK 26 local en `oracleJdk-26/` o cualquier JDK 21+ configurado en el `PATH`.
- **PostgreSQL 14 o superior**: En ejecución en `localhost:5432` con una base de datos creada (por defecto llamada `gestion` o según tu configuración).
- **Python 3.9 a 3.12** (para los modelos 3D):
  - Las dependencias se encuentran en `aplicacion_tres_d/aplicacion_tres_d/requeriments.txt` (`PyQt5`, `vtk`).
  - La aplicación cuenta con un entorno virtual preconfigurado en `aplicacion_tres_d/aplicacion_tres_d/.venv/`.

### 2. Configuración de Base de Datos
Edita el archivo [`config/app.properties`](config/app.properties) con las credenciales de tu servidor PostgreSQL:

```properties
db.url=jdbc:postgresql://localhost:5432/gestion
db.user=postgres
db.password=tu_password_aqui
```

Luego, inicializa la estructura ejecutando los scripts SQL en tu cliente PostgreSQL (pgAdmin, psql, DBeaver):
```bash
# 1. Crear las tablas e índices
psql -U postgres -d gestion -f db/schema.sql

# 2. Cargar usuarios, roles, curso de muestra y simulaciones 3D
psql -U postgres -d gestion -f db/test_users.sql
```

---

## ▶️ Ejecución del Proyecto

### Opción 1: Script Automatizado (Recomendada en Windows)
Ejecuta el script PowerShell en la raíz del proyecto:
```powershell
.\run.ps1
```
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
