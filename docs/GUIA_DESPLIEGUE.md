# Guía de Despliegue, Instalación y Mantenimiento
## Sistema de Gestión Académica y Simulador Anatómico Equino 3D
### Universidad de Pamplona — Facultad de Ciencias Agrarias

---

## 1. Requisitos del Sistema

### 1.1 Requisitos de Hardware

| Componente | Requisito Mínimo | Requisito Recomendado |
| :--- | :--- | :--- |
| **Procesador (CPU)** | Intel Core i3 o AMD Ryzen 3 (64-bit) | Intel Core i5 / AMD Ryzen 5 o superior |
| **Memoria RAM** | 4 GB | 8 GB o superior |
| **Gráficos (GPU)** | GPU integrada compatible con OpenGL 2.1 | Tarjeta gráfica dedicada compatible con OpenGL 3.3+ (NVIDIA / AMD / Intel Iris) |
| **Almacenamiento** | 2 GB de espacio libre en disco | 5 GB de espacio libre (SSD recomendado) |
| **Resolución de Pantalla** | 1280 × 720 (HD) | 1920 × 1080 (Full HD) o superior |
| **Dispositivo Apuntador** | Ratón con botón izquierdo, derecho y rueda de desplazamiento (*scroll*) | Mismo con alta precisión para navegación 3D |

### 1.2 Requisitos de Software y Sistema Operativo
- **Sistema Operativo**: Microsoft Windows 10 (64-bit) o Windows 11 (64-bit).
- **Controladores de Video**: Drivers de video actualizados del fabricante con soporte OpenGL acelerado.

---

## 2. Modalidad 1: Instalación para Usuarios Finales (Instalador Todo-en-Uno)

Esta modalidad está orientada a docentes, estudiantes y salas de cómputo donde no se requiera configurar herramientas de desarrollo ni servidores.

### 2.1 Características del Paquete
El instalador autosuficiente `GestionAcademica-1.1.0.exe` (ubicado en `build/dist/`) incluye de fábrica todos los componentes necesarios para su operación autónoma:
- **Java Runtime Environment (JRE)** optimizado mediante `jpackage`.
- **Python 3.14 Runtime Portable** con `PyQt5`, `VTK` y `numpy` preconfigurados.
- **PostgreSQL 18 Portable Embebido** con binarios del servidor y utilitarios (`initdb`, `pg_ctl`, `pg_dump`, `pg_restore`).
- Las 58 mallas tridimensionales, texturas y fichas anatómicas.

### 2.2 Proceso de Instalación Paso a Paso
1. Ejecute el archivo `GestionAcademica-1.1.0.exe`.
2. Siga las instrucciones del asistente de instalación de Windows. Por defecto, se instalará en:
   `C:\Program Files\GestionAcademica` (o la ruta seleccionada por el usuario).
3. Al finalizar, el instalador creará un acceso directo en el Escritorio y en el Menú Inicio.

### 2.3 Primer Arranque y Persistencia
- Al abrir la aplicación por primera vez, el sistema detecta que la base de datos no ha sido inicializada.
- Se crea automáticamente el cluster de base de datos en:
  `%LOCALAPPDATA%\GestionAcademica\basedatos\`
- Se ejecutan automáticamente las migraciones DDL (`schema.sql`, `cavidades_schema.sql`) y la carga de datos semilla (`test_users.sql`, `cavidades_fichas.sql`). Este proceso toma entre 5 y 15 segundos la primera vez.
- **Conservación de Datos**: Si la aplicación se desinstala o actualiza a una versión superior, los datos académicos, cuestionarios y notas almacenados en `%LOCALAPPDATA%` **se preservan intactos**.

---

## 3. Modalidad 2: Configuración del Entorno de Desarrollo

Para desarrolladores, investigadores o administradores que deseen compilar el código fuente, ejecutar pruebas unitarias o utilizar un servidor PostgreSQL corporativo.

### 3.1 Prerrequisitos de Desarrollo
1. **Java Development Kit (JDK 21 LTS o superior)**:
   - Se incluye soporte integrado para el JDK local en `oracleJdk-26/` o cualquier JDK moderno instalado en el sistema (`JAVA_HOME` configurado en variables de entorno).
2. **PostgreSQL (versión 14 a 18)**:
   - En ejecución en un servidor local o remoto (puerto predeterminado `5432`).
3. **Python (versión 3.9 o superior, probado en 3.14)**:
   - Instalado en el sistema con soporte para `pip` y entornos virtuales (`venv`).
4. **Git**:
   - Para clonar y versionar el repositorio.

---

### 3.2 Paso 1: Configurar la Base de Datos Externa

1. Conéctese a su servidor PostgreSQL mediante su herramienta preferida (psql, pgAdmin 4 o DBeaver) y cree la base de datos del proyecto:
   ```sql
   CREATE DATABASE gestion WITH ENCODING 'UTF8';
   ```

2. Ejecute en orden los cuatro scripts SQL para construir el esquema relacional y cargar la información semilla:
   ```bash
   # En CMD o PowerShell dentro de la raíz del proyecto:
   psql -U postgres -d gestion -f db/schema.sql
   psql -U postgres -d gestion -f db/test_users.sql
   psql -U postgres -d gestion -f db/cavidades_schema.sql
   psql -U postgres -d gestion -f db/cavidades_fichas.sql
   ```

3. Configure los datos de conexión en el archivo [`config/app.properties`](../config/app.properties):
   ```properties
   app.db.url=jdbc:postgresql://localhost:5432/gestion
   app.db.user=postgres
   app.db.password=tu_contrasena_aqui
   ```

---

### 3.3 Paso 2: Configurar el Entorno Virtual de Python (Modelos 3D)

Los modelos tridimensionales requieren librerías científicas y de visualización:

1. Abra una terminal PowerShell en la raíz del proyecto y navegue al directorio del módulo 3D:
   ```powershell
   cd aplicacion_tres_d\aplicacion_tres_d
   ```

2. Cree el entorno virtual `.venv`:
   ```powershell
   python -m venv .venv
   ```

3. Active el entorno virtual:
   ```powershell
   .\.venv\Scripts\Activate.ps1
   ```

4. Instale las dependencias especificadas en `requeriments.txt`:
   ```powershell
   pip install --upgrade pip
   pip install -r requeriments.txt
   ```
   *(Dependencias instaladas: `PyQt5`, `vtk`, `numpy`, `psycopg[binary]`)*.

5. Regrese a la raíz del proyecto:
   ```powershell
   cd ..\..
   ```

---

### 3.4 Paso 3: Compilación y Ejecución del Proyecto

#### Opción A: Mediante el Script de Automatización (Recomendada)
El script [`run.ps1`](../run.ps1) automatiza la detección del compilador, la compilación de clases en `out/`, la resolución de librerías en `lib/` y el arranque de la GUI:

```powershell
.\run.ps1
```

#### Opción B: Compilación y Ejecución Manual

**En Windows (PowerShell):**
```powershell
# 1. Compilación
javac -encoding UTF-8 -cp "lib/*" -d out (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName })

# 2. Ejecución
java -cp "out;lib/*" app.Main
```

**En Linux / macOS:**
```bash
# 1. Compilación
javac -encoding UTF-8 -cp "lib/*" -d out $(find src -name "*.java")

# 2. Ejecución
java -cp "out:lib/*" app.Main
```

---

### 3.5 Paso 4: Ejecución de la Suite de Pruebas Unitarias

Para validar la integridad de todos los módulos antes de desplegar:

```powershell
.\run_tests.ps1
```

El script compilará el código de prueba ubicado en `tests/` y ejecutará los 33 casos de prueba en el motor **JUnit 5 Standalone**, reportando el resultado en consola.

---

## 4. Modalidad 3: Generación del Instalador EXE (`run.ps1 -Mode package`)

Si desea generar un nuevo paquete instalador ejecutable `.exe` tras realizar modificaciones en el código fuente:

### 4.1 Herramientas Requeridas en el Equipo de Compilación
- **WiX Toolset 3.14**: Debe estar instalado en el sistema (`candle.exe` y `light.exe` en el PATH).
- **PostgreSQL 14 o 18**: Instalado en el sistema para extraer los binarios del runtime portable (`initdb.exe`, `pg_ctl.exe`, etc.).
- **Runtime de Python**: Preparado en `build/python-runtime` con las librerías VTK y PyQt5 preinstaladas.

### 4.2 Comando de Empaquetado
Ejecute en PowerShell con permisos estándar:

```powershell
# Generación con versión por defecto (1.1.0)
.\run.ps1 -Mode package

# O especificando una nueva versión y la ruta de PostgreSQL
.\run.ps1 -Mode package -Version "1.1.1" -PostgresSource "C:\Program Files\PostgreSQL\18"
```

El script ejecutará automáticamente las siguientes etapas:
1. Compila todas las clases Java en `out/`.
2. Empaqueta el archivo `GestionAcademica.jar`.
3. Prepara la estructura en `build/package-input/`.
4. Copia los runtimes portables de Python y PostgreSQL.
5. Invoca a `jpackage` y WiX Toolset para ensamblar el instalador nativo.
6. El instalador generado quedará disponible en `build/dist/GestionAcademica-<Version>.exe`.

---

## 5. Diagnóstico de Incidencias Comunes (Troubleshooting)

### 5.1 «No se pudo conectar a la base de datos PostgreSQL»
- **Causa 1**: El servicio de PostgreSQL no se encuentra iniciado.
  - *Solución*: Abra la consola de servicios de Windows (`services.msc`), busque `postgresql-x64-XX` y presione **Iniciar**.
- **Causa 2**: Credenciales incorrectas en `config/app.properties`.
  - *Solución*: Verifique que el usuario (`app.db.user`), clave (`app.db.password`) y nombre de base de datos coincidan con los de su servidor.
- **Causa 3**: La base de datos `gestion` no ha sido creada.
  - *Solución*: Ejecute `createdb -U postgres gestion` en la terminal.

### 5.2 «Error al abrir el Visor 3D / Fallo al importar VTK o PyQt5»
- **Causa 1**: Falta del entorno virtual en modo desarrollo.
  - *Solución*: Cree el entorno `.venv` en `aplicacion_tres_d/aplicacion_tres_d/` e instale los paquetes con `pip install -r requeriments.txt`.
- **Causa 2**: Falta de las librerías de tiempo de ejecución de Microsoft Visual C++.
  - *Solución*: Instale el paquete redistribuible *Visual C++ Redistributable 2015-2022 (x64)* desde el sitio oficial de Microsoft.
- **Causa 3**: Controlador de video sin aceleración OpenGL.
  - *Solución*: Actualice los controladores de su tarjeta gráfica (Intel Graphics, NVIDIA GeForce o AMD Radeon).

### 5.3 «El archivo .class está siendo usado por otro proceso»
- **Causa**: Un proceso de Java, un indexador de archivos o el antivirus mantiene abierto un archivo temporal en la carpeta `out/`.
  - *Solución*: Cierre cualquier proceso activo de `java.exe` o `javaw.exe` en el Administrador de Tareas y vuelva a ejecutar `.\run.ps1`.

### 5.4 Restablecimiento Manual de la Clave de Administrador
Si olvidó la contraseña de la cuenta `admin`, puede generar un nuevo hash BCrypt usando la utilidad integrada:

```powershell
java -cp "out;lib/*" app.tools.HashTool mi_nueva_clave
```

Copie la cadena hash generada (ej. `$2a$10$abcdef...`) y ejecute la siguiente sentencia SQL en PostgreSQL:

```sql
UPDATE usuarios SET password_hash = '$2a$10$abcdef...' WHERE username = 'admin';
```
