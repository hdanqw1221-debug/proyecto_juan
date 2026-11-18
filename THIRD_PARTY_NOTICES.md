# Avisos de terceros y licencias

Proyecto: «Simulador 3D de las cavidades torácica, abdominal e inguinal del equino usando Python, Java y Blender».
Autor del trabajo de grado: Juan José Santana, Universidad de Pamplona (Ingeniería en Telecomunicaciones).

## 1. Licencia de este proyecto

| Parte | Licencia | Archivo |
|---|---|---|
| **Código fuente** (Java, Python, SQL, scripts) | **GPL-3.0-or-later** | [LICENSE](LICENSE) |
| **Modelo 3D** (`cavidades/modelos/`, incluidos `escena.glb`, `obj/`, `manifiesto.json` y el `.blend`) y **fichas anatómicas** (`cavidades/datos/fichas_anatomicas.json` y `db/cavidades_fichas.sql`) | **CC BY-SA 4.0** | [LICENSE-CONTENIDO.md](LICENSE-CONTENIDO.md), texto en [licencias/CC-BY-SA-4.0.txt](licencias/CC-BY-SA-4.0.txt) |

**Por qué GPL.** El visor 3D usa **PyQt5, que se distribuye bajo GPLv3** (o bajo licencia comercial de Riverbank). Un programa
que enlaza con PyQt5 y se distribuye (por ejemplo, dentro del instalador `.exe`) debe publicarse bajo una licencia compatible
con GPL. Elegir GPL-3.0-or-later cumple esa condición sin cambiar código. Si en el futuro se quisiera una licencia permisiva
(MIT o Apache), habría que migrar el visor a **PySide6** (Qt for Python, LGPL) o comprar la licencia comercial de PyQt5.
Esta decisión de licencia corresponde al autor; ver «Pendientes» al final.

## 2. Dependencias de ejecución

| Componente | Versión | Licencia | Uso | Distribuido en el instalador |
|---|---|---|---|---|
| PyQt5 | 5.15.11 | GPL v3 (o comercial) | ventana y controles del visor | sí |
| Qt (PyQt5-Qt5) | 5.15.2 | LGPL v3 | motor gráfico de PyQt5 | sí |
| PyQt5-sip | 12.18.0 | SIP (ver el archivo de licencia del paquete) | enlace C++/Python | sí |
| VTK | 9.6.2 (Python 3.14) / 9.4.1 (< 3.14) | BSD 3-Clause | renderizado 3D | sí |
| numpy | 2.5.1 | BSD 3-Clause (con 0BSD, MIT, Zlib y CC0 en partes) | cálculo | sí |
| psycopg | 3.3.6 | LGPL v3 | lectura de fichas desde PostgreSQL (opcional) | sí |
| Python | 3.14.4 | PSF License | intérprete | sí (runtime embebido) |
| PostgreSQL JDBC (`postgresql-42.7.10.jar`) | 42.7.10 | BSD 2-Clause | acceso a la base desde Java | sí |
| jBCrypt (`jbcrypt-0.4.jar`) | 0.4 | ISC | cifrado de contraseñas | sí |
| Gson (`gson-2.11.0.jar`) | 2.11.0 | Apache 2.0 | análisis JSON | sí |
| PostgreSQL (servidor embebido) | 18.x | PostgreSQL License | base de datos | sí (`pgsql/`) |
| OpenJDK / Eclipse Temurin (runtime de `jpackage`) | 21+ | GPL v2 con Classpath Exception | máquina virtual Java | sí |

La excepción Classpath de OpenJDK y la licencia LGPL de Qt y psycopg permiten usarlos desde un programa GPLv3. Apache 2.0 (Gson)
es compatible con GPLv3.

## 3. Herramientas de desarrollo (no se distribuyen)

| Herramienta | Licencia | Nota |
|---|---|---|
| Blender 4.2 LTS | GPL v2 o posterior | genera el modelo. **La licencia de Blender no se extiende a los archivos que se crean con él**: el modelo se publica bajo CC BY-SA 4.0. |
| JUnit 5 (`junit-platform-console-standalone-1.10.1.jar`) | EPL 2.0 | solo pruebas; no está en el instalador |
| pytest, jsonschema | MIT | solo pruebas |

## 4. Material de referencia

- **Bradley, O. C. (1922)**, *The topographical anatomy of the thorax and abdomen of the horse* (W. Green & Son, Edimburgo):
  obra de **dominio público** (BHL / Wikimedia Commons). Las láminas de `cavidades/docs/referencias/bradley_1922/` se usan como
  guía de forma y topografía; ver el `README.md` de esa carpeta.
- **Contenido anatómico de las fichas**: se redacta a partir del documento de investigación
  `cavidades/docs/investigacion_anatomica_cavidades_equinas.md` y las fuentes que cita. Cada ficha marca como
  `pendiente_validacion` lo que un anatomista veterinario aún no ha validado.
- **Imágenes de la aplicación** (`assets/logo.png`, `assets/fondo_login.jpg`): identidad institucional de la Universidad de
  Pamplona; **no** están cubiertas por la licencia de este proyecto y se usan con fines académicos. Quien redistribuya el
  proyecto debe sustituirlas o contar con la autorización de la Universidad.

## 5. Autoría

- Código, modelo 3D procedimental y fichas: Juan José Santana, 2026, como parte del trabajo de grado.
- El modelo 3D es **esquemático y educativo**: no sustituye un atlas anatómico ni debe usarse para decisiones clínicas.

## Pendientes (decisión o verificación del autor)

1. Confirmar la licencia GPL-3.0-or-later para el código (elegida el 2026-11-18) y CC BY-SA 4.0 para el modelo y las fichas.
2. Confirmar con la Universidad de Pamplona si el logotipo y el fondo pueden redistribuirse con el repositorio.
3. Revisar el texto de las licencias de cada paquete en el instalador final (`python-runtime` y `pgsql` traen sus propios
   archivos de licencia, que deben conservarse al empaquetar).
