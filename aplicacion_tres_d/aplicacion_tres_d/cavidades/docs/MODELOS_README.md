# Modelos 3D de las cavidades torácica, abdominal e inguinal del caballo

Recurso del simulador del trabajo de grado. Contiene 58 estructuras anatómicas independientes y 3 envolturas de cavidad con identificador estable, sus fichas anatómicas (JSON y PostgreSQL) y el visor interactivo que las muestra.

La fuente de verdad anatómica es [investigacion_anatomica_cavidades_equinas.md](investigacion_anatomica_cavidades_equinas.md). Las fichas marcan con `pendiente_validacion` y `campos_pendientes` todo dato que no proviene de ese documento.

## Estructura

```
aplicacion_tres_d/aplicacion_tres_d/
  cavidades_equinas.py         # visor general (todas las cavidades)
  cavidad_toracica.py          # modelo inmersivo de la cavidad torácica
  cavidad_abdominal.py         # modelo inmersivo de la cavidad abdominal (con la transición pélvica)
  region_inguinal.py           # modelo inmersivo de la región inguinal
  cavidades/
    anatomia/referencia.py     # sistema de coordenadas, envolvente, costillas, diafragma
    anatomia/catalogo.py       # catálogo de estructuras: fuente única de IDs
    anatomia/especificacion.py # trayectorias, radios y parámetros de los tubos; registro de AJUSTES
    anatomia/organos/          # forma de cada órgano como campo implícito (un módulo por órgano):
                               #   corazon, pulmones, higado, estomago, bazo, pancreas, rinones (y adrenales),
                               #   vejiga, diafragma y tubos (tubos digestivos amoldados); contexto.py da
                               #   acceso a las formas vecinas
    herramientas/prototipo_organo.py  # malla un órgano sin Blender, mide su topología y dibuja 8 vistas
    blender/generar_cavidades.py  # script principal de Blender
    blender/primitivas.py      # tubos, campos implícitos, surface nets, suavizado, relajación
    blender/materiales.py      # paleta por sistema (editable)
    blender/exportar.py        # OBJ por objeto, GLB, .blend, manifiesto e informes
    datos/fichas_anatomicas.json, fichas.schema.json, generar_sql.py
    modelos/                   # salida: obj/, escena.glb, cavidades_equinas.blend, manifiesto.json,
                               #         informe_colisiones.md/.json, contexto_organos.json, vistas/*.png
    visor/                     # escena, selección, despiece (explosion.py), fichas, visor Qt, capturas,
                               # hoja_organo.py (8 vistas de una estructura para revisar su forma)
    docs/referencias/bradley_1922/  # láminas de dominio público usadas como guía
    tests/
db/cavidades_schema.sql        # migración aditiva (tablas nuevas)
db/cavidades_fichas.sql        # datos generados (idempotente)
src/app/.../AnatomicalStructure*.java  # modelo y DAO JDBC para la app Java
```

## Cómo regenerar

Requisitos: Blender 4.2 LTS (probado con 4.2.23), Python 3.14 con `vtk`, `PyQt5` y, opcionalmente, `psycopg[binary]` (ver `../requeriments.txt`). Para las pruebas también hacen falta `requirements-dev.txt` y PostgreSQL 14 o posterior.

Todos los comandos se ejecutan desde `cavidades/`.

```powershell
# 1. Modelos (≈5-9 min). Blender no está en el PATH en el equipo de desarrollo:
& "C:\Program Files\Blender Foundation\Blender 4.2\blender.exe" --background --factory-startup `
    --python blender/generar_cavidades.py -- --salida modelos/
#    Opciones: --cavidades toracica,abdominal,pelvica,inguinal,referencia   --sin-glb

# 2. Capturas de revisión (5 vistas) y del despiece de cada modelo inmersivo
python visor/capturas.py --sufijo todo
python visor/capturas_inmersivas.py

#    Prototipo rápido de un órgano (sin Blender), para iterar su forma:
python herramientas/prototipo_organo.py ABD_HIGADO --antes --contexto ABD_ESTOMAGO,ABD_RINON_DER
#    -> prototipos/ABD_HIGADO/hoja.png (nuevo), antes.png (actual) y metricas.json (piezas, túneles, solapes)

# 3. Fichas a PostgreSQL
python datos/generar_sql.py                      # escribe ../../../db/cavidades_fichas.sql
psql -U postgres -d gestion -f ../../../db/cavidades_schema.sql
psql -U postgres -d gestion -f ../../../db/cavidades_fichas.sql

# 4. Pruebas
python -m pytest tests

# 5. Visores
python ../cavidades_equinas.py      # general
python ../cavidad_toracica.py       # inmersivos
python ../cavidad_abdominal.py
python ../region_inguinal.py
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
| Órganos macizos | corazón, pericardio, pulmones, hígado, estómago, bazo, páncreas, riñones, adrenales, vejiga, testículos, epidídimos | Un módulo por órgano en `anatomia/organos/` con su forma base (`forma`) y su forma final con las impresiones de los vecinos (`campo`) → *surface nets* sobre rejilla regular |
| Tubos digestivos | duodeno, yeyuno, íleon, ciego, colon mayor y menor | Campo alrededor de la línea media relajada (haustras y tenias); cada tubo talla con suavidad a los vecinos ya construidos (`anatomia/organos/tubos.py`) y las asas del yeyuno y del colon descendente se separan antes de mallar |
| Otros tubos | tráquea, esófago, vasos, uréteres, conducto inguinal, cordón espermático, conducto deferente, vasos testiculares | Catmull-Rom remuestreado + tubo con radio variable (anillos) |
| Diafragma | — | Lámina sobre `x = f(y, z)` (centro tendinoso fino, periferia muscular gruesa) más los pilares, unidos y con los tres hiatos restados por booleano exacto |

Todas pasan por **remallado por vóxeles** (malla manifold), **suavizado de Taubin**, **recorte a la cavidad** con suavizado Laplaciano, y **diezmado** al presupuesto de triángulos.

**Contactos entre órganos.** Las vísceras se amoldan unas a otras dentro del propio campo, antes de mallar: cada órgano talla las impresiones de sus vecinos (impresión renal y gástrica del hígado, cara gástrica del bazo, impresión duodenal del páncreas, impresión cardíaca de los pulmones…). Un órgano que talla la impresión de otro lo declara en su conjunto `IMPRESIONES`, y ese otro ya no lo talla a él (si lo hicieran los dos quedaría un hueco). Así la revisión de colisiones ya no tiene que apartar vértices a la fuerza, que era lo que dejaba parches rasgados; los solapes residuales solo se informan.

**Revisión de la forma.** `tests/test_topologia.py` exige que cada estructura sea una sola pieza y sin túneles (asas fusionadas, tubos tallados como túneles, fragmentos sueltos), salvo los agujeros anatómicos: los tres hiatos del diafragma, el anillo pancreático (paso de la vena porta) y los anillos inguinales.

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

## Modelos inmersivos por cavidad

Cada cavidad tiene su propio modelo (`cavidad_toracica.py`, `cavidad_abdominal.py` y `region_inguinal.py`) y su script lo lanza la aplicación Java como cualquier otra práctica.

- **De lejos** se ve la cavidad como un todo: su *envoltura*. Para el tórax es el molde interno de la cavidad tapizado por la pleura costal, con las impresiones de las costillas. Para el abdomen, el molde tapizado por el peritoneo, con el arco costal y la línea alba. Para la región inguinal, la pared ventral engrosada alrededor del conducto y el escroto bilobulado. El clic sobre ella muestra la ficha de la cavidad.
- **Al acercarse** con la rueda del ratón, la envoltura se desvanece y los componentes se separan (despiece por proximidad, `visor/explosion.py`), con etiquetas flotantes que no se solapan. Al alejarse, la cavidad se vuelve a ensamblar. También puede manejarse a mano con el deslizador «Despiece de la cavidad».
- **Dirección de las piezas.** Siguen vectores anatómicos: pulmones hacia los lados, corazón ventral, tráquea craneal, aorta dorsal, diafragma caudal, hígado craneal, bazo y riñones hacia sus lados. La parte del desplazamiento que iría hacia la cámara se reorienta dentro del plano de la pantalla, para que ninguna pieza tape a las demás desde ningún ángulo.
- **Campo de visión.** Se abre de 30° a 58° a medida que las piezas se separan: el conjunto sigue a la vista y la sensación es de entrar en la cavidad.
- **Clic sobre una pieza:** ficha completa (todos los campos anatómicos, estado de validación, referencias) y datos del modelo 3D (identificador, lado, capa, sexo aplicable, medidas y volumen en el modelo, estructuras que comparten ficha). El doble clic o la tecla `F` encuadra la pieza.
- **Render realista:** materiales PBR con rugosidad por tejido, moteado orgánico por vértice, iluminación de tres puntos, oclusión ambiental (SSAO) y FXAA.

## Fidelidad: referencias usadas

No se encontraron modelos 3D de vísceras equinas con licencia libre y descarga directa: los existentes son comerciales o exigen cuenta. Las formas se ajustaron con las láminas de Bradley (1922), de dominio público ([docs/referencias/bradley_1922/](referencias/bradley_1922/README.md)):

- **Corazón** (fig. 10): masa ventricular asimétrica (borde caudal recto, craneal convexo), surcos coronario, paraconal y subsinuoso, cono arterioso, tronco pulmonar con sus dos arterias, bulbo aórtico, aurículas en oreja con el borde festoneado y muñones de las venas cavas y pulmonares. El pericardio es un saco liso que llega al esternón.
- **Pulmones** (fig. 8): semiconos con vértice romo, borde basal agudo en el receso costodiafragmático, escotadura cardíaca curva (grande en el izquierdo), lengüeta del lóbulo craneal, impresiones costales, cara mediastínica amoldada al pericardio con los surcos de la aorta, el esófago y las cavas, y lóbulo accesorio del pulmón derecho alrededor de la vena cava caudal.
- **Hígado** (fig. 56): placa única sobre el diafragma con lóbulos lateral y medial izquierdos, cuadrado, lateral derecho y caudado; fisuras del borde ventral, escotadura esofágica, surco de la vena cava, porta hepática e impresiones gástrica, duodenal, cólica y renal.
- **Estómago** (fig. 52): saco en J aplanado craneocaudalmente, con el saco ciego en continuidad y el anillo del píloro.
- **Bazo** (fig. 55): hoz aplicada a la pared izquierda, base ancha y truncada, ápice romo, surco del hilio y cara gástrica amoldada al estómago.
- **Páncreas**: glándula triangular lobulillada (cuerpo, lóbulos derecho e izquierdo) con el anillo pancreático.
- **Riñones** (fig. 65): derecho cordiforme y más craneal, izquierdo reniforme; hilio medial con el seno renal y la salida del uréter. Adrenales en lengüeta craneomediales.
- **Vejiga**: piriforme, con el cuello y un muñón de uretra, apoyada en el suelo de la pelvis.
- **Diafragma**: centro tendinoso, periferia muscular y pilares lumbares a los lados de la aorta.
- **Colon y ciego** (fig. 44): colones dorsal y ventral paralelos y contiguos, estrechamiento brusco de la flexura pélvica y ciego en coma con tenias y haustras.
- **Región inguinal:** testículo ovoide (~12 × 6,5 × 8 cm) comprimido lateralmente y algo más ancho en el polo craneal; epidídimo amoldado a su borde dorsolateral, con la cabeza sobre el polo craneal, el cuerpo estrecho y la cola voluminosa en el polo caudal, de donde sale el conducto deferente. Cordón espermático cónico (estrecho en el anillo profundo, ancho sobre el testículo por el plexo pampiniforme) que contiene los vasos testiculares y el deferente. Anillos inguinales en hendidura, el profundo más largo. Pared de contorno redondeado y escroto bilobulado con cuello corto: el testículo se sube a z = −0,07; en z = −0,20 (prompt) el cordón medía ~30 cm.

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
- Quedan solapes residuales entre vísceras (1-3 cm entre algunos tubos, como el colon descendente y el yeyuno, que en el animal se entremezclan), listados en `modelos/informe_colisiones.md`: el abdomen está muy lleno y la impronta de cada tubo se limita para no partirlo. También se listan contactos de vasos finos, que no se corrigen.
- El esófago se construye en la fase abdominal (se une al cardias relajado); si se generan solo las estructuras torácicas, no aparece.
- 43 de las 47 fichas tienen algún campo pendiente de validación, sobre todo la irrigación, que el documento de investigación no cubre.
- Con la oclusión ambiental activa, el visor inmersivo ronda los 25-30 fps en el equipo de desarrollo; si un equipo va lento, puede desactivarse en `configurar_render_realista`.
- La envoltura inguinal es una aproximación: pared engrosada y escroto, sin las capas de la pared (piel, túnica dartos, fascias).
