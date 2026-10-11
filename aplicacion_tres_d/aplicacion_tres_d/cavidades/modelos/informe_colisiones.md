# Informe de colisiones y ajustes

Generado automáticamente por `blender/generar_cavidades.py`. Profundidades en metros.

## Interpenetraciones > 1 cm entre estructuras no contiguas

| A | B | Profundidad inicial | Corregida | Deformado | Profundidad final |
|---|---|---|---|---|---|
| ABD_A_MESENTERICA_CRANEAL | ABD_DUODENO | 0.0239 | no (amoldado en el campo: solo informe) |  |  |
| ABD_COLON_DESCENDENTE | ABD_URETER_IZQ | 0.0115 | no (amoldado en el campo: solo informe) |  |  |
| ABD_COLON_DESCENDENTE | ABD_YEYUNO | 0.0302 | no (amoldado en el campo: solo informe) |  |  |
| ABD_COLON_DORSAL_DER | ABD_COLON_VENTRAL_DER | 0.0176 | no (amoldado en el campo: solo informe) |  |  |
| ABD_COLON_TRANSVERSO | ABD_YEYUNO | 0.0459 | no (amoldado en el campo: solo informe) |  |  |
| ABD_COLON_VENTRAL_DER | ABD_ILEON | 0.0188 | no (amoldado en el campo: solo informe) |  |  |
| ABD_DUODENO | ABD_HIGADO | 0.0179 | no (amoldado en el campo: solo informe) |  |  |
| ABD_VENA_CAVA_CAUDAL | TOR_PULMON_DER | 0.0153 | no (conducto fino: solo informe) |  |  |
| ING_ANILLO_SUPERFICIAL_DER | ING_CANAL_INGUINAL_DER | 0.0125 | no (conducto fino: solo informe) |  |  |
| ING_ANILLO_SUPERFICIAL_DER | ING_CORDON_ESPERMATICO_DER | 0.0106 | no (conducto fino: solo informe) |  |  |
| ING_ANILLO_SUPERFICIAL_IZQ | ING_CANAL_INGUINAL_IZQ | 0.0125 | no (conducto fino: solo informe) |  |  |
| ING_ANILLO_SUPERFICIAL_IZQ | ING_CORDON_ESPERMATICO_IZQ | 0.0106 | no (conducto fino: solo informe) |  |  |
| ING_CANAL_INGUINAL_DER | ING_CONDUCTO_DEFERENTE_DER | 0.0196 | no (conducto fino: solo informe) |  |  |
| ING_CANAL_INGUINAL_DER | ING_CORDON_ESPERMATICO_DER | 0.0171 | no (conducto fino: solo informe) |  |  |
| ING_CANAL_INGUINAL_DER | ING_VASOS_TESTICULARES_DER | 0.0194 | no (conducto fino: solo informe) |  |  |
| ING_CANAL_INGUINAL_IZQ | ING_CONDUCTO_DEFERENTE_IZQ | 0.0197 | no (conducto fino: solo informe) |  |  |
| ING_CANAL_INGUINAL_IZQ | ING_CORDON_ESPERMATICO_IZQ | 0.0171 | no (conducto fino: solo informe) |  |  |
| ING_CANAL_INGUINAL_IZQ | ING_VASOS_TESTICULARES_IZQ | 0.0197 | no (conducto fino: solo informe) |  |  |
| ING_CONDUCTO_DEFERENTE_DER | ING_CORDON_ESPERMATICO_DER | 0.0177 | no (conducto fino: solo informe) |  |  |
| ING_CONDUCTO_DEFERENTE_IZQ | ING_CORDON_ESPERMATICO_IZQ | 0.0176 | no (conducto fino: solo informe) |  |  |
| ING_CORDON_ESPERMATICO_DER | ING_VASOS_TESTICULARES_DER | 0.0176 | no (conducto fino: solo informe) |  |  |
| ING_CORDON_ESPERMATICO_IZQ | ING_VASOS_TESTICULARES_IZQ | 0.0177 | no (conducto fino: solo informe) |  |  |
| ING_EPIDIDIMO_DER | ING_TESTICULO_DER | 0.0102 | no (conducto fino: solo informe) |  |  |
| ING_EPIDIDIMO_IZQ | ING_TESTICULO_IZQ | 0.0102 | no (conducto fino: solo informe) |  |  |
| TOR_AORTA | TOR_TRAQUEA | 0.0216 | no (conducto fino: solo informe) |  |  |
| TOR_AORTA | TOR_VENA_CAVA_CRANEAL | 0.0177 | no (conducto fino: solo informe) |  |  |

## Desplazamiento de las líneas medias digestivas tras la relajación

| Objeto | Máximo (m) | Medio (m) |
|---|---|---|
| ABD_ESTOMAGO | 0.103 | 0.061 |
| ABD_DUODENO | 0.173 | 0.084 |
| ABD_YEYUNO | 0.17 | 0.074 |
| ABD_ILEON | 0.078 | 0.056 |
| ABD_CIEGO | 0.119 | 0.038 |
| ABD_COLON_VENTRAL_DER | 0.351 | 0.154 |
| ABD_FLEXURA_ESTERNAL | 0.223 | 0.146 |
| ABD_COLON_VENTRAL_IZQ | 0.242 | 0.091 |
| ABD_FLEXURA_PELVICA | 0.099 | 0.066 |
| ABD_COLON_DORSAL_IZQ | 0.185 | 0.058 |
| ABD_FLEXURA_DIAFRAGMATICA | 0.194 | 0.12 |
| ABD_COLON_DORSAL_DER | 0.168 | 0.112 |
| ABD_COLON_TRANSVERSO | 0.143 | 0.117 |
| ABD_COLON_DESCENDENTE | 0.141 | 0.053 |

## Ajustes respecto a las coordenadas del prompt

| Objeto | Cambio | Motivo |
|---|---|---|
| TOR_AORTA | Arco aórtico desplazado 3–4 cm a la izquierda (y 0,045–0,05). | En el prompt el arco coincidía con el esófago en (1,50; 0,01; 0,50); el documento indica que el esófago pasa a la derecha del arco. |
| TOR_ESOFAGO | Trayecto torácico con y ligeramente negativa (−0,005 a −0,012) y continuación hasta el cardias. | Esófago a la derecha del arco aórtico; el hiato se recalcula con x_diafragma (x ≈ 0,92). |
| TOR_AORTA | Tronco braquiocefálico ventral a la tráquea. | Evitar que cruce la luz traqueal (relación anatómica normal). |
| TOR_VENA_CAVA_CAUDAL | Foramen recalculado: x = x_diafragma(−0,04; 0,42) ≈ 1,18. | Indicado en el prompt. |
| ABD_ESTOMAGO | Saco ciego bajado a z = 0,52 (antes 0,57). | A z = 0,57 quedaba ~6 cm craneal al diafragma, fuera de la cavidad abdominal. |
| ABD_CIEGO | Cuerpo y ápice desplazados hacia el plano medio (y −0,02 a −0,05) y base ligeramente caudal. | En el prompt el cuerpo del ciego ocupaba el mismo volumen que los colones ventral y dorsal derechos (solape de 6–12 cm). |
| ABD_COLON_VENTRAL_DER | Inicio en el orificio cecocólico (0,66; −0,12; 0,42). | Continuidad con la base del ciego. |
| ABD_COLON_DORSAL_DER | Extremo caudal en (0,78; −0,15; 0,47). | Separarlo de la base del ciego. |
| ABD_COLON_TRANSVERSO | Trayecto (0,78; −0,15; 0,47) → (0,78; −0,05; 0,55) → (0,76; 0,06; 0,55). | Continuidad con el colon dorsal derecho ajustado; sigue craneal a la a. mesentérica craneal (x 0,68). |
| ABD_YEYUNO | Meandro determinista en capas (x 0,43–0,85; y 0,02–0,16; z 0,33–0,51), ~6 m. | Garantiza ausencia de autointersecciones; techo bajado para no invadir riñón izquierdo ni colon transverso. |
| ABD_COLON_DESCENDENTE | Meandro en dos capas (x 0,20–0,56; z 0,445–0,53), ≈2,5 m esquemáticos. | Cuadrante dorsal izquierdo; la longitud real (3–3,5 m) se indica en la ficha. |
| ABD_HIGADO | Proceso caudado añadido como lóbulo en (y −0,10; z 0,56). | Contacto con el riñón derecho (impresión renal). |
| ING_CONDUCTO_DEFERENTE | Ampolla a z = 0,375 (antes 0,32); recorrido caudomedial lateral a la vejiga. | A z = 0,32 quedaba dentro de la vejiga. |
| ING_TESTICULO | Centro a z = −0,07 (antes −0,20); estructuras escrotales referidas a él. | Escroto con cuello corto, como en el semental; con −0,20 el cordón medía ~30 cm. |
| ING_VASOS_TESTICULARES | Trayecto pegado a la pared dorsolateral (retroperitoneal). | El trayecto recto cruzaba el colon izquierdo y la flexura pélvica. |
| ABD_DUODENO | Porción ascendente a z ≈ 0,53 (antes 0,57–0,58), caudal a la raíz de la a. mesentérica craneal. | Pasa ventral al riñón izquierdo sin solaparlo (antes 2 cm) y caudal a la raíz del mesenterio, como en el animal. |
| ESTOMAGO_CIEGO_COLON_MAYOR | Radios ×0,90 (estómago) y ×0,85 (ciego y colon mayor). | Con los radios nominales el tubo digestivo ocuparía ~90 % de la cavidad abdominal; se reduce a ~65 %. |
| TODOS_LOS_TUBOS_DIGESTIVOS | Líneas medias relajadas automáticamente (ver informe de colisiones). | Separación determinista por prioridad y ajuste a la cavidad; el desplazamiento de cada órgano se informa. |
