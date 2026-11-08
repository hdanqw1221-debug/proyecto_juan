# Sistema de Gestión Académica y Simulador Anatómico Equino 3D
## Repositorio Central de Documentación Oficial
### Universidad de Pamplona — Facultad de Ciencias Agrarias

Bienvenido al centro integral de documentación del **Sistema de Gestión Académica y Simulador Anatómico Equino 3D**, una plataforma concebida para la enseñanza interactiva, gestión académica, evaluación por competencias y exploración tridimensional de la anatomía equina en medicina veterinaria y zootecnia.

---

## 📚 Mapa de Navegación de la Documentación

La documentación se encuentra estructurada en cuatro manuales técnicos y operativos exhaustivos:

```
docs/
├── README.md              # Este documento (Índice y visión general de la documentación)
├── MANUAL_TECNICO.md      # Arquitectura de software, patrones, módulos Java/Python y seguridad
├── BASE_DE_DATOS.md       # Diagrama E-R, normalización 3FN, diccionario de datos y respaldos
├── MANUAL_USUARIO.md      # Guía paso a paso por roles (Admin, Profesor, Estudiante) y visor 3D
└── GUIA_DESPLIEGUE.md     # Instalación, empaquetado jpackage, PostgreSQL embebido y solución de problemas
```

---

## 📑 Resumen de Documentos

| Documento | Audiencia Principal | Contenido Destacado |
| :--- | :--- | :--- |
| **[Manual Técnico y de Arquitectura](MANUAL_TECNICO.md)** | Arquitectos de Software, Desarrolladores, Evaluadores Técnicos | • Arquitectura multicapa Java 21 + Python 3.14 VTK/PyQt5.<br>• Patrones de diseño (DAO, Service Layer, SessionManager, Factory).<br>• Protocolo IPC Java ↔ Python con salida estándar JSON y auditoría.<br>• Motor de despiece progresivo (*explosion engine*) y ray-casting.<br>• Suite de pruebas unitarias JUnit 5 (33 tests). |
| **[Manual de Base de Datos](BASE_DE_DATOS.md)** | Administradores de BD, Desarrolladores Backend | • Diagrama Entidad-Relación en Mermaid (16 tablas).<br>• Diccionario de datos campo a campo con restricciones e índices.<br>• Especificación de esquemas JSONB (simulaciones y resultados).<br>• Motor PostgreSQL embebido portable y políticas de respaldo. |
| **[Manual de Usuario y Operación](MANUAL_USUARIO.md)** | Administradores, Profesores, Estudiantes, Evaluadores | • Flujos operativos para Administrador, Profesor y Estudiante.<br>• Importación masiva de usuarios vía CSV con previsualización en vivo.<br>• Creación de evaluaciones con banco de preguntas multimedia e imágenes.<br>• Generación de Certificados Académicos con código criptográfico SHA-256.<br>• Guía de interacción con los 4 visores 3D de cavidades equinas. |
| **[Guía de Despliegue y Empaquetado](GUIA_DESPLIEGUE.md)** | Equipos DevOps, Soporte TI, Usuarios Finales | • Requisitos de hardware y software (OpenGL, CPU, RAM).<br>• Instalación mediante paquete autosuficiente Windows (.EXE todo-en-uno).<br>• Configuración del entorno de desarrollo (JDK 21+, PostgreSQL 14+, Python .venv).<br>• Generación de instaladores con `jpackage` y WiX Toolset.<br>• Guía de resolución de problemas (*troubleshooting*). |

---

## 🏛️ Contexto del Proyecto

El sistema fue diseñado como una herramienta pedagógica de vanguardia para la **Universidad de Pamplona**, integrando dos dimensiones complementarias:

1. **Gestión Académica Completa (LMS)**:
   - Control de acceso basado en roles (**RBAC**): `ADMINISTRADOR`, `PROFESOR` y `ESTUDIANTE`.
   - Administración de programas académicos, asignaturas y matriculación de estudiantes.
   - Motor de evaluación flexible con banco de preguntas, soporte de imágenes diagnósticas (`BYTEA`), tiempo límite regresivo y límite de intentos.
   - Libro de notas con auditoría de respuestas, exportación estandarizada a CSV (RFC 4180 / UTF-8 BOM) y emisión de certificados digitales de aprobación con hash de verificación.

2. **Simulador Tridimensional de Anatomía Equina**:
   - Modelado morfológico preciso de **58 estructuras anatómicas** correspondientes a las cavidades torácica, abdominal e inguinal del caballo.
   - Generación determinista en **Blender 4.2 LTS** mediante campos implícitos, *surface nets* y remallado por vóxeles manifold.
   - Visores interactivos de alto rendimiento desarrollados en **PyQt5 + VTK**.
   - Funcionalidad de **despiece inmersivo progresivo**: al realizar zoom hacia el modelo, los órganos se separan armónicamente en el espacio tridimensional revelando relaciones anatómicas y estructuras profundas.
   - Selección por clic con ray-casting e inspección de fichas anatómicas completas basadas en la *Nomina Anatomica Veterinaria* (NAV).

---

## ⚡ Enlaces Rápidos a la Raíz del Proyecto

- [README Principal del Repositorio](../README.md): Instrucciones resumidas para clonar y ejecutar.
- [Script de Automatización PowerShell](../run.ps1): Compilación, ejecución y empaquetado del instalador.
- [Script de Pruebas Unitarias](../run_tests.ps1): Ejecución de la suite JUnit 5.
- [Esquema Principal de Base de Datos](../db/schema.sql): Tablas del sistema académico y auditoría.
- [Esquema de Cavidades 3D](../db/cavidades_schema.sql): Tablas del catálogo anatómico y modelos.
- [Datos Anatómicos Semilla](../db/cavidades_fichas.sql): Fichas completas de las 58 estructuras.
