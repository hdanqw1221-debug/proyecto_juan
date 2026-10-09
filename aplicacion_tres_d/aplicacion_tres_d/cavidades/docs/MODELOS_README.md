# Modelos 3D de las cavidades torácica, abdominal e inguinal del caballo

Recurso del simulador del trabajo de grado. Contiene 58 estructuras independientes con identificador estable, sus fichas anatómicas (JSON y PostgreSQL) y el visor interactivo que las muestra.

La fuente de verdad anatómica es [investigacion_anatomica_cavidades_equinas.md](investigacion_anatomica_cavidades_equinas.md). Las fichas marcan con `pendiente_validacion` y `campos_pendientes` todo dato que no proviene de ese documento.

## Estructura

```
aplicacion_tres_d/aplicacion_tres_d/
  cavidades_equinas.py         # punto de entrada que lanza la app Java (script3d)
  cavidades/
    anatomia/referencia.py     # sistema de coordenadas, envolvente, costillas, diafragma
    anatomia/catalogo.py       # catálogo de estructuras: fuente única de IDs
    anatomia/especificacion.py # trayectorias, radios y parámetros; registro de AJUSTES
    blender/generar_cavidades.py  # script principal de Blender
    blender/primitivas.py      # tubos, campos implícitos, surface nets, suavizado, relajación
    blender/materiales.py      # paleta por sistema (editable)
    blender/exportar.py        # OBJ por objeto, GLB, .blend, manifiesto e informes
    datos/fichas_anatomicas.json, fichas.schema.json, generar_sql.py
    modelos/                   # salida: obj/, escena.glb, cavidades_equinas.blend, manifiesto.json,
                               #         informe_colisiones.md/.json, vistas/*.png
    visor/                     # escena, selección, repositorio de fichas, visor Qt, capturas
    tests/
db/cavidades_schema.sql        # migración aditiva (tablas nuevas)
db/cavidades_fichas.sql        # datos generados (idempotente)
src/app/.../AnatomicalStructure*.java  # modelo y DAO JDBC para la app Java
```

## Cómo regenerar

Requisitos: Blender 4.2 LTS (probado con 4.2.23), Python 3.14 con `vtk`, `PyQt5` y, opcionalmente, `psycopg[binary]` (ver `../requeriments.txt`). Para las pruebas también hacen falta `requirements-dev.txt` y PostgreSQL 14 o posterior.

Todos los comandos se ejecutan desde `cavidades/`.

```powershell
# 1. Modelos (≈2,5 min). Blender no está en el PATH en el equipo de desarrollo:
& "C:\Program Files\Blender Foundation\Blender 4.2\blender.exe" --background --factory-startup `
    --python blender/generar_cavidades.py -- --salida modelos/
#    Opciones: --cavidades toracica,abdominal,pelvica,inguinal,referencia   --sin-glb

# 2. Capturas de revisión (5 vistas)
python visor/capturas.py --sufijo todo

# 3. Fichas a PostgreSQL
python datos/generar_sql.py                      # escribe ../../../db/cavidades_fichas.sql
psql -U postgres -d gestion -f ../../../db/cavidades_schema.sql
psql -U postgres -d gestion -f ../../../db/cavidades_fichas.sql

# 4. Pruebas
python -m pytest tests

# 5. Visor
python ../cavidades_equinas.py
```

La generación es determinista: semillas fijas y ningún dato aleatorio ni con marca de tiempo. Las mismas entradas producen la misma salida.

## Convenciones

- **Coordenadas** (`anatomia/referencia.py`): metros; +X craneal, +Y izquierda, +Z dorsal. Origen en la abertura pélvica craneal, el plano medio y el suelo de la cavidad a la altura del xifoides. El GLB, por la norma glTF, se exporta con Y arriba; los OBJ y el manifiesto usan el sistema anatómico.
- **Identificadores**: `CAVIDAD_ESTRUCTURA[_LADO]` (`TOR_`, `ABD_`, `PEL_`, `ING_`, `REF_`). `ficha_id` puede ser compartida por los lados izquierdo y derecho. Los nombres de nodo del GLB son los `objeto_id`, como pide el Anexo J de la tesis.
- **Propiedades personalizadas** de cada objeto de Blender: `objeto_id`, `ficha_id`, `cavidad`, `sistema`, `lado`, `seleccionable`, `capa`, `color_hex`, `opacidad`, `visible_por_defecto`, `sexo_aplicable` y `manifold`.
- **Mallas**: cerradas y manifold, normales hacia fuera, entre 2 000 y 15 000 triángulos por estructura (las referencias quedan fuera de este límite).

## Cómo se construye cada estructura

| Tipo | Estructuras | Método |
|---|---|---|
| Tubos | tráquea, esófago, vasos, uréteres, intestinos, colon, conductos inguinales | Catmull-Rom remuestreado + tubo con radio variable (haustras, tenias, anillos) |
| Campos implícitos | pulmones, corazón, pericardio, hígado, bazo, páncreas, riñones, adrenales, vejiga, testículos, anillos, mediastino | Campo de distancia aproximada → *surface nets* sobre rejilla regular |
| Diafragma | — | Lámina sobre `x = f(y, z)` recortada por la envolvente, con los tres hiatos restados por booleano exacto |

Todas pasan por **remallado por vóxeles** (malla manifold), **suavizado de Taubin**, **recorte a la cavidad** (envolvente y empuje según la normal del diafragma) con suavizado Laplaciano, y **diezmado** al presupuesto de triángulos.

Los pulmones se definen como la región del hemitórax que queda craneal al diafragma y al borde basal (así queda el receso costodiafragmático) y fuera del mediastino, el corazón y los tubos mediastínicos. El pulmón izquierdo tiene además la escotadura cardíaca y el derecho, el lóbulo accesorio.

## Decisiones respecto al prompt (revisar con el director)

El registro completo, con sus motivos, está en `anatomia/especificacion.py` (`AJUSTES`) y en `modelos/informe_colisiones.md`. Resumen:

1. **Empaquetado digestivo.** Con los radios nominales, estómago, ciego, colon e intestinos ocuparían ~90 % de la cavidad abdominal de la envolvente, algo imposible para tubos rígidos (en el animal las vísceras se deforman y llenan los huecos). Los radios del ciego y del colon mayor se reducen un 15 % y los del estómago un 10 %. Las fichas dan las dimensiones reales.
2. **Relajación de líneas medias.** Las trayectorias del prompt se interpenetraban (por ejemplo, ciego y colon ventral y dorsal derechos, con solapes de 6 a 12 cm). Un paso determinista separa los tubos digestivos según su prioridad, rodeando hígado, bazo, riñones, páncreas, vasos y conductos inguinales y respetando la cavidad. El desplazamiento de cada órgano se informa; hasta ~35 cm en el colon ventral derecho.
3. **Coordenadas corregidas.**
   - El arco aórtico se desplaza a la izquierda del esófago; en el prompt coincidían.
   - El saco ciego del estómago se baja, porque quedaba craneal al diafragma.
   - La ampolla del conducto deferente se sube, porque quedaba dentro de la vejiga.
   - La arteria testicular se lleva pegada a la pared, porque cruzaba el colon.
4. **Longitudes esquemáticas.** El yeyuno mide ~6 m (17–28 m reales) y el colon descendente, ~2,5 m (3–3,5 m reales). Se indica en las fichas.
5. **Nombres de tablas** en plural (`cavidades`, `sistemas_anatomicos`, `estructuras_anatomicas`, `modelos_3d`), siguiendo el esquema existente (`usuarios`, `cursos`…).
6. **Ficha:** se añaden `campos_pendientes` y `sexo_aplicable`, como pide el Anexo J de la tesis.

## Visor

- Un actor por estructura, con `vtkPolyDataNormals` y *depth peeling* para las transparencias.
- **Clic** (desplazamiento menor de 5 px): `vtkCellPicker` con localizadores de celdas. **Clic repetido** (≤ 6 px): la siguiente estructura a lo largo del rayo.
- Teclas: `1-5` cavidades · `H` ocultar selección · `A` mostrar todo · `T` resto translúcido · `R` restablecer cámara · `Esc` deseleccionar · `F1-F5` vistas · buscador por nombre.
- **Fichas:** desde PostgreSQL (credenciales de `config/app.properties` o la variable `CAVIDADES_DB_DSN`); si no hay conexión, desde `datos/fichas_anatomicas.json`.
- **Eventos JSON por stdout** (`inicio`, `seleccion`, `cierre`), un primer paso del protocolo bidireccional que propone la tesis (§11.5).
- **Medido en el equipo de desarrollo:** carga en 2,9 s, selección en 125 ms y 36 fps.

Para usarlo en clase, el docente crea una práctica 3D con `"script3d": "cavidades_equinas.py"`. `db/cavidades_fichas.sql` la crea automáticamente solo si existe la materia de ejemplo «Anatomia Veterinaria Equina I».

## Refinamiento anatómico

El pipeline admite sustituir cualquier malla sin tocar el código. Si existe `modelos/refinados/<objeto_id>.obj` (mismo sistema de coordenadas), el exportador la usa en lugar de la procedural e informa en `informe_colisiones.md` de la desviación del centroide y del volumen. Vías previstas:

1. Escultura en Blender sobre `modelos/cavidades_equinas.blend`, usando como referencia las láminas de Budras et al. (2012) y de Dyce et al. (2018).
2. Segmentación de TC o RM de cadáver en 3D Slicer, registrada al sistema de coordenadas.
3. Fotogrametría de piezas de disección.

En todos los casos, con licencia compatible y con validación del director.

## Limitaciones conocidas

- Es una base procedural: la topografía, las proporciones, las relaciones y las asimetrías son correctas, pero la forma fina es esquemática.
- Quedan interpenetraciones residuales entre órganos, listadas en `modelos/informe_colisiones.md`. Destacan el colon dorsal derecho con el hígado (~5 cm) y el colon descendente con el yeyuno (~3 cm, que en el animal se entremezclan). También se listan contactos de vasos finos, que no se corrigen.
- El esófago se construye en la fase abdominal (se une al cardias relajado); si se generan solo las estructuras torácicas, no aparece.
- 40 de las 44 fichas tienen algún campo pendiente de validación, sobre todo la irrigación, que el documento de investigación no cubre.
