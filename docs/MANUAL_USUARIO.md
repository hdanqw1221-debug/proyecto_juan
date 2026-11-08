# Manual de Usuario y Operación Funcional
## Sistema de Gestión Académica y Simulador Anatómico Equino 3D
### Universidad de Pamplona — Facultad de Ciencias Agrarias

---

## 1. Introducción y Acceso al Sistema

El **Sistema de Gestión Académica y Simulador Anatómico Equino 3D** es una plataforma integral orientada a la formación de médicos veterinarios y zootecnistas de la Universidad de Pamplona. Permite administrar materias, programar evaluaciones multimedia, consultar estadísticas académicas y explorar de manera interactiva modelos anatómicos tridimensionales con despiece progresivo.

### 1.1 Inicio de Sesión
Al arrancar la aplicación, se despliega la pantalla de autenticación:

1. Ingrese su **Nombre de Usuario** o correo institucional.
2. Digite su **Contraseña**.
3. Haga clic en el botón **«Iniciar Sesion»**.
4. Si su cuenta tiene asignados múltiples roles (por ejemplo, `ADMINISTRADOR` y `PROFESOR`), el sistema le permitirá seleccionar en cuál perfil desea ingresar durante la sesión.

### 1.2 Credenciales Preconfiguradas para Demostraciones
Para entornos de prueba y sustentación, el sistema cuenta con cuatro cuentas precargadas en el script [`db/test_users.sql`](../db/test_users.sql):

| Usuario | Contraseña | Rol Principal | Alcance |
| :--- | :--- | :--- | :--- |
| `admin` | `admin123` | **ADMINISTRADOR** | Gestión total de la plataforma, usuarios, auditoría y respaldos. |
| `profesor` | `profesor123` | **PROFESOR** | Diseño de cuestionarios, asignación de prácticas 3D y libro de notas. |
| `juan` | `juan123` | **ESTUDIANTE** | Alumno matriculado en la materia *Anatomía Veterinaria*. |
| `maria` | `maria123` | **ESTUDIANTE** | Alumna matriculada en la materia *Anatomía Veterinaria*. |

### 1.3 Cambio de Contraseña de Perfil
Cualquier usuario autenticado puede actualizar su propia contraseña haciendo clic en el botón de perfil ubicado en la esquina superior derecha del menú principal. La nueva clave debe tener al menos 6 caracteres y combinar caracteres válidos.

---

## 2. Manual del Perfil Administrador

El administrador dispone de facultades para coordinar la infraestructura académica e institucional de la plataforma a través de las siguientes pestañas:

### 2.1 Pestaña «Usuarios»
Permite gestionar la totalidad de las cuentas de la institución:
- **Creación Manual**: Diligencie los campos *Usuario*, *Email*, *Contraseña* y seleccione los roles a otorgar (`ADMINISTRADOR`, `PROFESOR`, `ESTUDIANTE`). Presione **«Guardar Usuario»**.
- **Edición y Estado**: Seleccione un usuario en la tabla para modificar su correo, alternar su estado entre **Activo / Inactivo** (un usuario inactivo no puede iniciar sesión) o reasignar sus roles.
- **Importación Masiva vía CSV**:
  1. Haga clic en **«Descargar Plantilla CSV»** para obtener el archivo base con el formato esperado (`username,email,password,roles`).
  2. Complete la lista de docentes o alumnos en su hoja de cálculo y guarde como CSV UTF-8.
  3. Presione **«Importar Usuarios (CSV)»** y seleccione el archivo.
  4. Se abrirá una ventana de **Previsualización Interactiva** que valida fila por fila: las filas correctas se resaltan en verde y los errores (duplicados, formato de correo erróneo o roles inexistentes) en rojo con el motivo específico.
  5. Haga clic en **«Confirmar Importación»** para procesar los registros válidos de forma atómica.

### 2.2 Pestaña «Cursos»
Gestión del catálogo de asignaturas académicas:
- **Crear Materia**: Ingrese el *Nombre* del curso (ej. *Anatomía Quirúrgica Equina*), una descripción pedagógica y seleccione el docente titular en la lista desplegable.
- **Modificación y Retiro**: Permite actualizar el docente asignado o eliminar asignaturas que no posean evaluaciones vinculadas activas.

### 2.3 Pestaña «Estudiante-Materia»
Formalización de la matrícula institucional:
- Seleccione la materia en la lista superior.
- Seleccione el estudiante en la lista de alumnos disponibles.
- Presione **«Asignar Estudiante»**. La relación se reflejará inmediatamente en el portal del estudiante y en el libro de notas del profesor.
- Para desvincular a un estudiante, selecciónelo en la tabla y haga clic en **«Eliminar Asignacion»**.

### 2.4 Pestañas «Roles» y «Passwords»
- **Roles**: Asignación rápida y revocación de permisos institucionales por usuario.
- **Passwords**: Permite al administrador restablecer forzosamente la contraseña de cualquier usuario que haya olvidado su clave de acceso.

### 2.5 Pestaña «Logs» (Auditoría Forense)
Muestra la bitácora inmutable de eventos institucionales en tiempo real:
- Registra inicios de sesión, cierres de sesión, creación de cuestionarios, modificaciones de notas, sesiones de simulación 3D ejecutadas y respaldos.
- Permite filtrar por usuario responsable o rango de fechas.

### 2.6 Pestaña «Simuladores 3D»
Permite al administrador comprobar el estado del entorno de Python, la presencia de librerías (`PyQt5`, `vtk`, `numpy`) y abrir directamente cualquiera de los 4 visores anatómicos para diagnóstico o verificación técnica.

### 2.7 Pestaña «Sistema» (Telemetría y Copias de Seguridad)
- **Monitoreo de Salud**: Muestra el total de usuarios registrados, usuarios activos, cursos dictados, asignaciones vigentes, estado de la conexión a PostgreSQL y modo de base de datos (embebida vs externa).
- **Copias de Seguridad (Backup)**: Presione el botón **«Crear Copia de Seguridad Ahora»** para generar un respaldo binario comprimido de la base de datos con fecha y hora automática.
- **Restauración**: Permite seleccionar un archivo `.dump` previamente creado y restaurar la base de datos de manera íntegra.

---

## 3. Manual del Perfil Profesor

El docente dispone de un entorno completo para la gestión del aprendizaje, diseño de instrumentos de evaluación y supervisión de simulaciones.

### 3.1 Pestaña «Cuestionarios»
Permite crear y estructurar exámenes académicos con retroalimentación:
- **Parámetros Generales de la Evaluación**:
  - *Materia*: Seleccione la asignatura en la que se aplicará la prueba.
  - *Título y Descripción*: Identificación clara para los alumnos.
  - *Límite de Intentos*: Cantidad de veces que cada estudiante puede presentar el examen (por defecto 1 intento).
  - *Tiempo Límite (minutos)*: Duración del examen cronometrada en pantalla (por defecto 60 minutos).
  - *Fecha de Vencimiento*: Selector de calendario y hora (`DateTimePicker`) tras la cual el examen se bloquea automáticamente.
- **Banco de Preguntas Multimedia**:
  - Redacte el enunciado de la pregunta.
  - Asigne el puntaje ponderado que aporta a la nota final.
  - **Soporte de Imágenes Médicas**: Presione **«Cargar Imagen»** para adjuntar fotografías anatómicas, ecografías, radiografías o piezas de disección que los estudiantes verán durante la prueba.
  - Ingrese las alternativas de respuesta y marque la casilla de verificación correspondiente a la opción u opciones correctas.
- **Editor Masivo (`QuestionBatchEditorDialog`)**: Facilita la creación ágil y consecutiva de múltiples preguntas sin cerrar el formulario.
- **Previsualización en Vivo (`QuestionnairePreviewDialog`)**: Permite al docente experimentar la evaluación exactamente como la verá el estudiante antes de publicarla oficialmente.

### 3.2 Pestaña «Simulaciones»
Administración de actividades prácticas sobre modelos tridimensionales:
- **Botón «+ Practicas de cavidades 3D»**: Con un solo clic, el sistema genera automáticamente para el curso una práctica guiada para cada uno de los 4 visores que no haya sido creada aún, precargando sus objetivos pedagógicos, escenarios clínicos e instrucciones anatómicas.
- **Configuración Personalizada**: Permite modificar el título, escenario clínico (p. ej., *«Caso de cólico por impacto de colon mayor»*) y preguntas guía para la sesión interactiva.
- **Previsualización 3D Directa**: El docente puede abrir y manipular el modelo desde su propio panel para preparar la clase práctica.

### 3.3 Pestaña «Notas»
Libro de calificaciones centralizado:
- Seleccione la materia y la evaluación para cargar la lista de estudiantes con sus respectivos intentos, fecha de presentación, duración y calificación obtenida.
- **Calificación Asistida / Manual**: El docente puede ajustar la nota final si la evaluación incluyó preguntas abiertas o consideraciones especiales.
- **Exportación Oficial a CSV**: Al hacer clic en **«Exportar Notas (CSV)»**, el sistema genera un archivo compatible con el estándar **RFC 4180** y codificación **UTF-8 con BOM**, asegurando que Microsoft Excel lo abra directamente con tildes, caracteres especiales y columnas perfectamente delimitadas.
- **Revisión Detallada de Respuestas**: Al presionar **«Revision Detallada»** sobre un intento seleccionado, se abre el diálogo [`ExamAttemptReviewDialog`](../src/app/ui/ExamAttemptReviewDialog.java), mostrando:
  - Pregunta por pregunta con su enunciado e imagen asociada.
  - La opción exacta que marcó el alumno.
  - La opción correcta definida por la clave de respuestas.
  - El puntaje obtenido en el reactivo.
  - La retroalimentación pedagógica generada.

### 3.4 Pestaña «Estadísticas»
Módulo de analítica del rendimiento estudiantil:
- Gráficos circulares interactivos ([`PieChartPanel`](../src/app/ui/PieChartPanel.java)) con el porcentaje de aprobados vs reprobados.
- Rango de calificaciones más frecuentes y nota promedio del grupo.
- Tasa de participación sobre el total de alumnos matriculados.

---

## 4. Manual del Perfil Estudiante

El portal del estudiante brinda un acceso simple y moderno a sus compromisos formativos:

### 4.1 Pestaña «Materias»
Muestra la lista de asignaturas en las que se encuentra matriculado, el nombre del profesor titular a cargo y su correo de contacto.

### 4.2 Pestaña «Practicas»
Presenta las prácticas de anatomía asignadas por el docente:
- Muestra el nombre de la práctica, el objetivo pedagógico y el escenario clínico a analizar.
- Haga clic en **«Abrir Practica 3D»** para iniciar el visor correspondiente en subproceso optimizado.

### 4.3 Pestaña «Modelos 3D»
Catálogo libre de autoaprendizaje:
- Permite acceder de forma directa e independiente a los 4 visores anatómicos equinos en cualquier momento, sin necesidad de que medie una tarea calificada.

### 4.4 Pestaña «Evaluaciones»
Portal de presentación de exámenes:
- Lista las evaluaciones programadas, fecha límite de entrega, tiempo concedido y número de intentos disponibles.
- Para iniciar, seleccione el examen y presione **«Presentar Evaluacion»**.

#### Entorno de Presentación del Examen (`ExamDialog`)
Al abrir el examen, se despliega una interfaz inmersiva diseñada para evitar distracciones:
1. **Temporizador Regresivo**: En la parte superior derecha se visualiza un cronómetro en tiempo real (HH:MM:SS). Si el tiempo expira, la prueba se envía y califica automáticamente con las respuestas guardadas hasta ese momento.
2. **Barra de Progreso**: Informa la cantidad de preguntas respondidas sobre el total.
3. **Cuadrícula de Navegación Lateral**: Permite saltar directamente a cualquier pregunta o identificar visualmente cuáles reactivos faltan por contestar.
4. **Visualizador de Imágenes**: Si la pregunta posee una imagen diagnóstica adjunta, se muestra en alta resolución junto al enunciado.
5. **Guardado Preventivo**: Cada selección de respuesta se almacena en memoria de manera inmediata.
6. **Finalización**: Al terminar, presione **«Finalizar Examen»** y confirme el envío.

### 4.5 Pestaña «Calificaciones» y Emisión de Certificados
- Consulta inmediata de la nota final obtenida en cada examen presentado.
- **Revisar Respuestas**: Permite al estudiante examinar su intento con fines de estudio (ver qué preguntas acertó y en cuáles erró con su respectiva justificación).
- **Generación de Certificado Académico de Aprobación**:
  - Si el estudiante aprueba una evaluación con una nota igual o superior a **3.0 / 5.0**, se habilita el botón **«Generar Certificado»**.
  - El sistema compila y abre automáticamente en el navegador web un certificado institucional de alta presentación formal con las siguientes características:
    - Escudos y membrete institucional de la Universidad de Pamplona.
    - Nombre del estudiante en mayúsculas sostenidas y documento.
    - Nombre del curso y de la evaluación aprobada.
    - Mención de honor según la calificación:
      - **4.8 a 5.0**: *Aprobado con Mención de Honor y Excelencia Summa Cum Laude*.
      - **4.5 a 4.7**: *Aprobado con Distinción y Mención Sobresaliente*.
      - **4.0 a 4.4**: *Aprobado con Calificación Sobresaliente*.
      - **3.0 a 3.9**: *Aprobado Satisfactoriamente*.
    - **Código Criptográfico de Verificación SHA-256** único (formato `UP-YYYY-XXXX`).
    - Botón de impresión directa a documento **PDF** listo para firma y archivo curricular.

---

## 5. Guía de Uso del Simulador Anatómico Equino 3D

Los visores tridimensionales constituyen el núcleo visual y pedagógico del proyecto. Funcionan sobre **PyQt5** con renderizado gráfico acelerado por hardware mediante **VTK**.

### 5.1 Catálogo de Visores Disponibles

```
                                  VISORES ANATÓMICOS EQUINOS
                                               │
             ┌─────────────────────┬───────────┴───────────┬─────────────────────┐
             ▼                     ▼                       ▼                     ▼
     Cavidad Torácica      Cavidad Abdominal       Región Inguinal        Visor General
    (cavidad_toracica)    (cavidad_abdominal)     (region_inguinal)    (cavidades_equinas)
```

1. **Cavidad Torácica (`cavidad_toracica.py`)**:
   - Pulmón derecho (lóbulo craneal, caudal y accesorio), pulmón izquierdo, tráquea, corazón, pericardio seroso y fibroso, esófago torácico, aorta descendente, venas cavas craneal y caudal, y diafragma completo.
2. **Cavidad Abdominal (`cavidad_abdominal.py`)**:
   - Estómago (saco ciego, curvaturas mayor y menor), duodeno, flexuras duodenales, yeyuno, íleon, ciego (base, cuerpo y vértice), colon mayor (colon ventral derecho, flexura esternal, colon ventral izquierdo, flexura pélvica, colon dorsal izquierdo, flexura diafragmática, colon dorsal derecho), colon menor transverso y descendente, recto, hígado, páncreas, bazo, riñones, glándulas adrenales, uréteres y vejiga urinaria.
3. **Región Inguinal (`region_inguinal.py`)**:
   - Pared abdominal inguinal, anillo inguinal superficial, anillo inguinal profundo, conducto inguinal, fascia espermática, cordón espermático, arteria y vena testicular, conducto deferente, testículo y epidídimo (cabeza, cuerpo y cola).
4. **Visor General de Cavidades (`cavidades_equinas.py`)**:
   - Escena completa con las 58 estructuras anatómicas simultáneas, con conmutadores laterales para encender y apagar cavidades de forma independiente.

---

### 5.2 Controles de Ratón y Navegación Espacial

| Acción | Control del Ratón | Efecto en la Escena 3D |
| :--- | :--- | :--- |
| **Rotación Orbital** | Clic Izquierdo + Arrastrar | Gira la escena en 360° alrededor del centro anatómico de interés. |
| **Traslación / Paneo** | Clic Derecho + Arrastrar | Desplaza horizontal y verticalmente la cámara sobre el plano de vista. |
| **Zoom Dinámico** | Rueda del Ratón (Scroll) | Acerca o aleja la cámara del punto apuntado por el cursor. |
| **Despiece Progresivo** | Rueda del Ratón hacia Adelante | **Efecto de Explosión Inmersiva**: al aproximarse a las estructuras, los órganos exteriores se desplazan de manera coordinada revelando órganos profundos. |
| **Selección de Órgano** | Clic Izquierdo sobre la pieza | Resalta la estructura seleccionada con un contorno brillante y abre la **Ficha Anatómica**. |
| **Selección en Profundidad** | Clics Repetidos en el mismo punto | Permite seleccionar estructuras internas situadas detrás de órganos semitransparentes. |

---

### 5.3 Ficha Anatómica Veterinaria Integrada

Al seleccionar cualquier estructura anatómica, se despliega en el lateral de la pantalla la ficha técnica completa estandarizada según la *Nomina Anatomica Veterinaria* (NAV):

- **Encabezado**: Nombre en español, denominación oficial en latín, cavidad a la que pertenece y sistema orgánico.
- **Descripción General**: Morfología, peso, dimensiones medias y características físicas en un caballo adulto (~500 kg).
- **Topografía y Proyecciones Superficiales**: Localización espacial exacta referenciada con costillas, espacios intercostales (EIC) y líneas clínicas (línea de la tuberosidad coxal, línea de la punta del hombro y línea del olécranon).
- **Relaciones Anatómicas**: Estructuras y órganos con los que establece contacto directo.
- **Medios de Fijación**: Ligamentos, mesos, pliegues peritoneales o pleurales que sostienen el órgano.
- **Irrigación, Inervación y Drenaje**: Vasos arteriales nutricios, venas de retorno y plexos nerviosos autónomos.
- **Consideraciones Clínicas**: Principales patologías equinas asociadas (cólicos por impacto, desplazamientos, vólvulos, pleuroneumonías, hernias inguinales, rotura gástrica).
- **Exploración Semiológica**: Guía práctica para auscultación torácica, ecografía abdominal o palpación transrectal.
