# Investigación anatómica: cavidades torácica, abdominal e inguinal del caballo

Documento base para el simulador 3D del trabajo de grado (visor Python/VTK + aplicación académica Java/PostgreSQL). El foco está en el **contenido de las cavidades** (vísceras, grandes vasos, límites serosos y conductos), no en músculos, esqueleto ni piel; estos solo aparecen como referencias topográficas.

> **Advertencia de validación.** Las medidas son valores de referencia para un caballo adulto de ~500 kg (alzada ~1,60 m) tomados de la literatura clásica. Varían con raza, edad, condición corporal, estado de llenado del tubo digestivo y fase respiratoria. Toda cifra marcada con (≈) debe ser revisada por el director o un anatomista veterinario antes de la sustentación.

---

## 1. Marco de referencia y convenciones

**Nomenclatura.** Se usan términos de la *Nomina Anatomica Veterinaria* (NAV, 6.ª ed., 2017) con su equivalente en español. Términos de posición: craneal, caudal, dorsal, ventral, medial, lateral; en el tórax se usan las proyecciones sobre costillas (C) y espacios intercostales (EIC).

**Fórmula vertebral del caballo.** C7, T18, L6 (a veces L5), S5, Cd 15–21. Hay **18 pares de costillas**: 8 esternales (verdaderas) y 10 asternales que forman el **arco costal**.

**Topografía de superficie útil para el modelo.** La pared lateral se divide con tres líneas horizontales de referencia muy usadas en clínica: la línea de la tuberosidad coxal (tuber coxae), la línea de la articulación del hombro (punta del hombro) y la línea del olécranon (codo). La mayor parte de las proyecciones de órganos se expresa como "EIC n a la altura de la línea X".

---

## 2. Cavidad torácica (cavitas thoracis)

### 2.1 Límites y organización
La cavidad torácica tiene forma de cono truncado aplanado lateralmente, con la **abertura craneal (apertura thoracis cranialis)** estrecha y ovalada a nivel de la 1.ª costilla, y la base cerrada por el **diafragma**, muy convexo hacia craneal. Por la gran cúpula diafragmática, buena parte del "tórax óseo" caudal contiene en realidad vísceras abdominales (abdomen intratorácico: hígado, estómago, bazo, flexuras del colon).

Contiene dos **cavidades pleurales** (derecha e izquierda) separadas por el **mediastino**. En el caballo el mediastino caudal es delgado y a menudo **fenestrado o incompleto**, por lo que neumotórax y derrames pleurales suelen ser **bilaterales**: es un dato clínico importante para la ficha.

### 2.2 Pulmones (pulmo dexter et sinister)
- **Lobulación.** Fisuras interlobulares poco marcadas. Pulmón izquierdo: lóbulo craneal y caudal (separados solo por la escotadura cardíaca). Pulmón derecho: lóbulo craneal, caudal y **accesorio**; este último se aloja en el receso mediastínico, entre el mediastino caudal y el pliegue de la vena cava caudal (plica venae cavae). No hay lóbulo medio diferenciado.
- **Escotadura cardíaca.** Más amplia en el lado izquierdo (3.ª–6.ª costilla, ventral), lo que permite el contacto del corazón con la pared costal izquierda (ventana de auscultación y ecocardiografía).
- **Borde basal y campo de auscultación.** Triángulo cuyo borde craneal es la línea tricipital, el dorsal la musculatura epiaxial y el caudoventral una línea curva que pasa por el **EIC 16 a la altura de la tuberosidad coxal**, el **EIC 11 a la altura de la punta del hombro** y el **EIC 6 a la altura del olécranon**. Caudal a ese borde queda el **receso costodiafragmático**, ocupado solo por pleura (no por pulmón).
- **Bronquios.** La tráquea se bifurca en bronquios principales derecho e izquierdo; el caballo **no tiene bronquio traqueal** (a diferencia de rumiantes y cerdo).
- **Clínica.** Pleuroneumonia (derrame frecuentemente bilateral), asma equina (antes obstrucción recurrente de vías aéreas), hemorragia pulmonar inducida por el ejercicio (afecta campos caudodorsales), neumotórax bilateral.

### 2.3 Corazón (cor) y pericardio
- **Posición.** Entre la **3.ª y 6.ª costilla** (craneocaudalmente ≈2.ª–6.ª), con ~**60 % a la izquierda** del plano medio. El eje base–ápice es oblicuo: base dorsocraneal a media altura del tórax; ápice ventral, cerca del esternón a nivel de la **5.ª–6.ª costilla**, separado pocos centímetros del diafragma.
- **Peso.** ≈0,7–1 % del peso corporal (≈3,5–5 kg en un caballo de 500 kg); mayor en caballos de carrera entrenados.
- **Focos de auscultación.** Izquierda: pulmonar EIC 3, aórtico EIC 4 (a la altura de la punta del hombro, ventral a ella), mitral EIC 5 (cerca de la línea del codo). Derecha: tricúspide EIC 3–4. En el caballo pueden oírse cuatro ruidos cardíacos.
- **Pericardio.** Fibroso + seroso (parietal y visceral). El **ligamento esternopericárdico** lo fija al esternón. El caballo no posee *os cordis* (presente en bovinos).
- **Clínica.** Fibrilación auricular (arritmia patológica más frecuente), bloqueo AV de 2.º grado fisiológico en reposo, insuficiencias valvulares (mitral, aórtica), pericarditis (rara).

### 2.4 Tráquea y bronquios principales
Tubo de ≈48–60 cartílagos en "C" abiertos dorsalmente. Entra por la abertura craneal en posición ventral a la columna y se bifurca **dorsal a la base del corazón, aproximadamente a nivel del EIC 5**. Diámetro ≈5–6 cm. Clínica: endoscopia, lavado traqueal y aspirado transtraqueal; colapso traqueal (raro).

### 2.5 Esófago (porción torácica)
Longitud total ≈1,2–1,5 m. En el cuello va a la izquierda de la tráquea; en el tórax se sitúa **dorsal a la tráquea**, pasa a la derecha del arco aórtico y continúa en el mediastino dorsal hasta el **hiato esofágico** (en el pilar derecho del diafragma, ≈EIC 13–14, porción dorsal). Su musculatura es **estriada en los 2/3 craneales y lisa en el tercio caudal**. La desembocadura en el estómago es oblicua y con un esfínter cardial muy potente. Clínica: obstrucción esofágica ("choke", más frecuente en porción cervical), estenosis, megaesófago.

### 2.6 Grandes vasos torácicos
- **Aorta torácica.** Nace del ventrículo izquierdo, forma el **arco aórtico** a la izquierda y da un único **tronco braquiocefálico** (en el caballo la subclavia izquierda nace de él). La aorta descendente corre ventral a las vértebras, ligeramente a la izquierda, da arterias intercostales dorsales y la arteria broncoesofágica, y atraviesa el **hiato aórtico** entre los pilares diafragmáticos (≈T18–L1). Clínica: rotura de la raíz aórtica (sementales durante la monta).
- **Vena cava craneal.** Formada a la entrada del tórax por las yugulares y venas axilares/subclavias; drena al atrio derecho.
- **Vena cava caudal (porción torácica).** Atraviesa el **foramen de la vena cava** en el centro tendinoso del diafragma, a la derecha del plano medio, y corre dentro del **pliegue de la vena cava** hasta el atrio derecho.

### 2.7 Mediastino
Septo medio formado por las dos pleuras mediastínicas. Contiene corazón y pericardio, tráquea terminal, esófago, aorta, venas cavas, conducto torácico, timo (vestigial en el adulto), nervios frénicos y vagos, y linfonodos mediastínicos. Su fenestración caudal explica la bilateralidad de muchos procesos pleurales.

### 2.8 Diafragma (límite tóraco-abdominal)
- **Forma.** Cúpula muy pronunciada cuyo vértice llega, en espiración, a nivel del **EIC 6** en el tercio ventral-medio del tórax. La inserción periférica es muy oblicua: ventralmente en el xifoides y cartílagos costales caudales (≈8.º), y dorsalmente en las últimas costillas y en las vértebras lumbares craneales mediante los **pilares** (el derecho mucho mayor).
- **Partes.** Porción muscular (esternal, costal, lumbar) y **centro tendinoso**.
- **Aberturas.** Hiato aórtico (dorsal, entre pilares), hiato esofágico (pilar derecho), foramen de la vena cava (centro tendinoso, a la derecha).
- **Inervación.** Nervio frénico (C5–C7).
- **Clínica.** Hernia diafragmática (congénita o traumática), aleteo diafragmático sincrónico ("thumps") por alteraciones electrolíticas.

---

## 3. Cavidad abdominal (cavitas abdominis)

### 3.1 Límites y organización
Limitada cranealmente por el diafragma, dorsalmente por las vértebras lumbares y la musculatura sublumbar, lateral y ventralmente por la pared abdominal, y caudalmente se continúa sin separación con la **cavidad pélvica** a nivel de la abertura pélvica craneal. Está tapizada por el **peritoneo**. El **omento mayor** del caballo es pequeño y no cubre las vísceras como en el perro. El tracto gastrointestinal ocupa la mayor parte del volumen; el **intestino grueso (ciego + colon)** contiene la mayor parte de la ingesta.

Cuadrantes útiles para la ficha y la palpación rectal: dorsal izquierdo (yeyuno, colon descendente, bazo, riñón izquierdo), dorsal derecho (base del ciego, colon dorsal derecho, duodeno descendente, riñón derecho), ventral izquierdo (colon ventral y dorsal izquierdos), ventral derecho (cuerpo del ciego, colon ventral derecho).

### 3.2 Estómago (ventriculus, gaster)
- **Capacidad.** Pequeña en proporción: ≈8–15 L.
- **Posición.** Casi completamente dentro de la jaula costal, a la **izquierda**, entre ≈**EIC 9 y 15**, dorsal (no contacta con la pared ventral cuando está vacío). La curvatura mayor contacta con el bazo y el diafragma; la menor con el hígado.
- **Partes.** Saco ciego (saccus caecus), cardias, cuerpo, porción pilórica (antro y canal) y píloro, que se proyecta a la derecha.
- **Mucosa.** Región **no glandular** (escamosa, esofágica), que incluye el saco ciego, separada de la **glandular** (cardial, fúndica propia y pilórica) por el **margo plicatus**.
- **Clínica.** El esfínter cardial y el ángulo oblicuo de entrada del esófago hacen el vómito excepcional (riesgo de rotura gástrica). Síndrome de úlcera gástrica equina (lesiones escamosas junto al margo plicatus), impactación gástrica. Exploración: gastroscopia (endoscopio ≥3 m), ecografía en EIC 10–15 izquierdos.

### 3.3 Intestino delgado
- **Duodeno (≈1 m).** Porción craneal con ampolla y flexura sigmoidea en contacto con el hígado; **papila duodenal mayor** (conducto colédoco + conducto pancreático) a ≈10–15 cm del píloro, dentro de un divertículo; papila menor (conducto pancreático accesorio) enfrente. Porción descendente a la derecha, en contacto con hígado, riñón derecho y base del ciego; flexura caudal alrededor de la base del ciego; porción transversa que cruza de derecha a izquierda caudal a la raíz del mesenterio; porción ascendente unida al colon descendente por el **pliegue duodenocólico** (referencia quirúrgica). Clínica: duodenitis-yeyunitis proximal.
- **Yeyuno (≈17–28 m).** Suspendido por un mesenterio largo (hasta ≈50 cm), lo que le da gran movilidad; ocupa sobre todo el **cuadrante dorsal izquierdo**, entremezclado con el colon descendente. Clínica: lipoma pedunculado estrangulante, vólvulo, atrapamiento en el foramen epiploico, hernia inguinal.
- **Íleon (≈0,5–0,7 m).** Pared muscular gruesa; unido a la tenia dorsal del ciego por el **pliegue ileocecal**; desemboca en el **ostium ileale** (papila ileal) en la curvatura menor de la base del ciego. Clínica: impactación ileal, intususcepción ileocecal, hipertrofia muscular; *Anoplocephala perfoliata* en la unión ileocecal.

### 3.4 Ciego (caecum)
- **Dimensiones.** ≈1–1,25 m de longitud; ≈25–35 L.
- **Forma y posición.** En forma de coma. **Base** dorsal en el flanco derecho (fosa paralumbar derecha, alcanza la región sublumbar); **cuerpo** que desciende cranioventralmente a lo largo de la pared ventral derecha; **ápice** sobre el suelo abdominal cerca del cartílago xifoides.
- **Tenias.** Cuatro (dorsal, ventral, medial y lateral) que forman haustras (saculaciones).
- **Orificios.** Ostium ileale y **orificio cecocólico** en la curvatura menor de la base.
- **Fisiología.** Cámara de fermentación microbiana (ácidos grasos volátiles).
- **Clínica.** Impactación cecal, tiflitis, perforación (asociada a tenias), intususcepción cecocecal/cecocólica, timpanismo (trocarización en el flanco derecho). Tenia ventral y base palpables por recto.

### 3.5 Colon mayor (colon ascendens)
- **Dimensiones.** ≈3–3,7 m; ≈50–60 L (puede ser mayor).
- **Disposición en doble "U" (herradura doblada).** Colon ventral derecho → flexura esternal → colon ventral izquierdo → **flexura pélvica** → colon dorsal izquierdo → flexura diafragmática → colon dorsal derecho → colon transverso.
- **Tenias y diámetros.** Colon ventral derecho e izquierdo con 4 tenias y saculaciones; en la flexura pélvica el diámetro se reduce bruscamente (≈8–9 cm) y queda 1 tenia; colon dorsal izquierdo con 1 tenia, sin saculaciones; colon dorsal derecho con 3 tenias, se dilata en la **ampolla del colon** (diámetro muy grande) y se estrecha en embudo hacia el colon transverso.
- **Fijación.** Solo el colon dorsal derecho y la base del ciego están fijos (pared dorsal y pliegue cecocólico). Las porciones izquierdas quedan **libres**, lo que explica los desplazamientos y vólvulos.
- **Clínica por segmento.** Flexura pélvica: impactación más frecuente, palpable por recto, sitio habitual de enterotomía. Colon izquierdo: desplazamiento dorsal izquierdo con **atrapamiento nefroesplénico**, desplazamiento dorsal derecho, vólvulo del colon mayor. Colon dorsal derecho: colitis dorsal derecha por AINE; enterolitos en la transición al colon transverso. Flexura esternal: en el suelo, caudal al xifoides. Flexura diafragmática: caudal a diafragma e hígado.

### 3.6 Colon transverso y colon descendente (colon menor)
- **Transverso.** Corto, cruza de derecha a izquierda **craneal a la arteria mesentérica craneal**, fijo a la pared dorsal; estrechez donde se alojan enterolitos.
- **Descendente (≈3–3,5 m).** Dos tenias, saculaciones, forma las **bolas fecales**; mesocolon largo; en el cuadrante dorsal izquierdo junto al yeyuno. Clínica: impactación, fecalitos, lipomas estrangulantes.

### 3.7 Hígado (hepar)
- **Peso.** ≈1–1,5 % del peso corporal en el adulto (≈5 kg).
- **Posición.** Totalmente intratorácico, aplicado contra la cara abdominal del diafragma, más a la **derecha**; en caballos viejos el lóbulo derecho se atrofia y el órgano se desplaza a la izquierda.
- **Lóbulos.** Lateral izquierdo, medial izquierdo, cuadrado, derecho y caudado con **proceso caudado** (con impresión renal del riñón derecho). **No hay vesícula biliar**; el conducto colédoco desemboca con el pancreático en la papila duodenal mayor.
- **Medios de fijación.** Ligamentos coronario, triangulares derecho e izquierdo, falciforme con ligamento redondo, hepatorrenal; omento menor hacia estómago y duodeno.
- **Proyección.** Derecha ≈EIC 10–16 en su tercio medio; izquierda ≈EIC 6–9 ventral (ventanas ecográficas). Biopsia hepática habitual en el lado derecho (≈EIC 12–14).
- **Clínica.** Encefalopatía hepática, colelitiasis, hepatitis sérica (enfermedad de Theiler), intoxicación por alcaloides pirrolizidínicos.

### 3.8 Bazo (lien, splen)
- **Forma.** Falciforme; base dorsal ancha bajo las **últimas 3 costillas** izquierdas y el flanco; el extremo ventral se dirige cranioventralmente a lo largo de la pared izquierda.
- **Ligamentos.** **Nefroesplénico** (con el riñón izquierdo), gastroesplénico, frenicoesplénico.
- **Fisiología.** Gran reservorio de eritrocitos; la contracción esplénica durante el ejercicio eleva el hematocrito.
- **Clínica.** Atrapamiento nefroesplénico del colon izquierdo; borde caudal palpable por recto; ecografía en EIC 10–17 izquierdos.

### 3.9 Páncreas
Triangular, con lóbulo derecho, cuerpo y lóbulo izquierdo; dorsal, a nivel de ≈16–17 costillas. La **vena porta atraviesa el anillo pancreático** (anulus pancreatis). Conducto pancreático a la papila mayor; accesorio a la papila menor. Clínica: pancreatitis (rara).

### 3.10 Riñones, adrenales y uréteres (retroperitoneales)
- **Riñón derecho.** **Cordiforme** (forma de corazón de naipe), ≈15 cm; bajo los extremos dorsales de las 2–3 últimas costillas y la apófisis transversa de L1; más craneal; en contacto con el proceso caudado del hígado. No palpable por recto.
- **Riñón izquierdo.** **Reniforme** (de habichuela), más largo y estrecho; desde la 18.ª costilla hasta L2–L3; polo caudal **palpable por recto**; forma con el bazo el espacio nefroesplénico.
- **Pelvis renal.** Con recesos terminales; glándulas mucosas en pelvis y uréter proximal (orina equina normalmente mucoide y turbia).
- **Adrenales.** Craneomediales a cada riñón, alargadas.
- **Uréteres (≈70 cm).** Retroperitoneales; desembocan oblicuamente en la cara dorsal del cuello vesical. Clínica: uréter ectópico (incontinencia en potrancas), ureterolitos.

### 3.11 Grandes vasos abdominales
- **Aorta abdominal.** Ventral a las vértebras lumbares; ramas: tronco celíaco (≈T18–L1), **arteria mesentérica craneal (≈L1)**, renales, testiculares/ováricas, mesentérica caudal; termina a nivel de ≈L5–L6 dividiéndose en las ilíacas externas e internas.
- **Arteria mesentérica craneal.** Irriga yeyuno, íleon, ciego, colon ascendente y transverso. Clínica: arteritis verminosa por larvas de *Strongylus vulgaris* (cólico tromboembólico).
- **Vena cava caudal.** Dorsal y a la derecha de la aorta; se aloja en el surco del borde dorsal del hígado antes de atravesar el diafragma.

---

## 4. Transición pélvica (referencias)
- **Recto.** ≈30 cm; parte peritoneal craneal y parte retroperitoneal (ampolla). Vía de **palpación rectal**. Clínica: desgarros rectales (más frecuentemente en la pared dorsal).
- **Vejiga urinaria.** Pélvica cuando está vacía; con repleción se proyecta hacia el abdomen. Clínica: urolitos de carbonato cálcico; rotura vesical neonatal (uroperitoneo, más en potros machos).

---

## 5. Región y canal inguinal (canalis inguinalis)

### 5.1 Concepto
El canal inguinal no es una cavidad cerrada sino un **paso oblicuo en la pared abdominal ventral caudal**, entre el **músculo oblicuo interno del abdomen** (craneomedial) y la **aponeurosis del oblicuo externo** (caudolateral), de ≈10–15 cm (≈). Se dirige de craneodorsal-lateral a caudoventral-medial.

### 5.2 Componentes
- **Anillo inguinal profundo (interno).** Abertura hacia la cavidad abdominal, delimitada por el borde caudal del oblicuo interno y el ligamento inguinal.
- **Anillo vaginal.** En el macho, abertura del **proceso vaginal** del peritoneo dentro del anillo profundo; palpable por recto craneolateral al borde del pubis (en el semental admite 1–2 dedos).
- **Anillo inguinal superficial (externo).** Hendidura en la aponeurosis del oblicuo externo, craneal al pubis y lateral al prepucio.
- **Contenido en el macho.** Proceso/túnica vaginal con el **cordón espermático** (conducto deferente, arteria testicular, plexo pampiniforme, linfáticos y nervios), músculo cremáster (fuera de la túnica vaginal), vasos pudendos externos y nervio genitofemoral.
- **Contenido en la hembra.** Vasos pudendos externos, linfáticos de la glándula mamaria y nervio genitofemoral; sin proceso vaginal relevante.

### 5.3 Órganos asociados (macho)
- **Testículo.** Ovoide, ≈10–12 cm de largo, con el **eje mayor casi horizontal**; descenso alrededor del nacimiento (≈30 días antes a ≈10 días después) guiado por el gubernáculo.
- **Epidídimo.** Dorsolateral al testículo; la cola es caudal y se fija a la túnica vaginal por el ligamento de la cola del epidídimo.
- **Conducto deferente.** De la cola del epidídimo por el canal inguinal hacia el abdomen, gira caudomedialmente en el pliegue genital, cruza dorsal al uréter y forma una **ampolla** bien desarrollada antes de desembocar en el colículo seminal. Palpable por recto.

### 5.4 Clínica
Hernia inguinal indirecta (a través del anillo vaginal; sementales tras monta o ejercicio), hernia escrotal congénita en potros (a menudo autolimitada), hernia directa (rotura de túnicas), **criptorquidia** inguinal o abdominal, torsión del cordón espermático, evisceración tras castración a través del anillo vaginal (base de las técnicas de castración abierta, cerrada y semicerrada).

---

## 6. Implicaciones para el modelado 3D
1. **Abdomen intratorácico.** El diafragma debe modelarse como cúpula profunda; hígado, estómago, bazo y flexuras diafragmática y esternal quedan craneales al arco costal.
2. **Asimetría izquierda-derecha.** Corazón y estómago a la izquierda; hígado, base del ciego, duodeno descendente y riñón derecho (craneal) a la derecha.
3. **Receso costodiafragmático.** El borde basal del pulmón queda craneal a la inserción diafragmática.
4. **Volumen digestivo.** Ciego + colon mayor dominan el abdomen; el yeyuno se representa con longitud reducida de forma esquemática (documentarlo en la ficha).
5. **Selección por capas.** Estructuras profundas (bronquios, vasos, páncreas) requieren ocultar o atravesar las superficiales: el visor debe permitir clic repetido para "profundizar".
6. **Fidelidad.** Un modelo procedural da topografía y proporciones correctas, pero la forma fina exige esculpir sobre referencias (láminas, fotografías de disección) o segmentar estudios de TC/RM de cadáver.

---

## 7. Referencias recomendadas
- Getty R. *Sisson and Grossman's The Anatomy of the Domestic Animals*. 5.ª ed. Saunders; 1975.
- Dyce KM, Sack WO, Wensing CJG (Singh B, ed.). *Textbook of Veterinary Anatomy*. 5.ª ed. Elsevier; 2018.
- König HE, Liebich HG. *Anatomía de los animales domésticos*. Ed. Médica Panamericana (ediciones recientes).
- Budras KD, Sack WO, Röck S. *Anatomy of the Horse*. 6.ª ed. Schlütersche; 2012.
- Nickel R, Schummer A, Seiferle E. *The Viscera of the Domestic Mammals*. Parey.
- International Committee on Veterinary Gross Anatomical Nomenclature. *Nomina Anatomica Veterinaria*. 6.ª ed.; 2017.
- Auer JA, Stick JA, et al. *Equine Surgery*. Elsevier (ediciones recientes).
- Reed SM, Bayly WM, Sellon DC. *Equine Internal Medicine*. Elsevier (ediciones recientes).
