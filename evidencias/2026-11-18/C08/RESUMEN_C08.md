# C08 - ensayos de estabilidad del visor (acercar, girar, acercar)

Equipo: Intel Core i7-4600U, Intel HD Graphics (controlador 20.19.15.5070), Windows 10 LTSC 2019, Python 3.14.4,
VTK 9.6.2, PyQt5 5.15.11. Herramienta: `cavidades/herramientas/ensayo_estres.py` (un proceso por ensayo; cierre = código
de salida distinto de 0 o falta de la marca final).

| Configuración | Código | Modelos | Ensayos | Cierres |
|---|---|---|---|---|
| Normal (rueda cada 25 ms, 10 pasos por fase) | original `8518bc9` | toracica, abdominal, inguinal, general | 80 | **0** |
| `QT_OPENGL=software` | original `8518bc9` | abdominal, inguinal | 40 | **0** |
| Agresivo (rueda cada 5 ms, 40 pasos por fase) | original `8518bc9` | abdominal, inguinal | 40 | **0** |
| Normal | **nuevo** (faulthandler + guarda de `rueda`) | los 4 | 80 | **0** |

**No se reprodujo el fallo.** La tesis (PF-37) reporta un cierre por violación de segmento en 25 de 26 ensayos del modelo
abdominal en otro entorno (renderizado por software, VTK 9.7.1). En este equipo, con 160 ensayos del código original, no hubo
ningún cierre. Por tanto **la causa no queda confirmada**. La hipótesis es que la rueda del ratón llega mientras las piezas del
despiece se mueven (`_rueda` → selector de VTK/OpenGL sobre una escena a medio actualizar), y por eso `rueda` se ignora durante la
transición y captura excepciones; es una mitigación preventiva. Hace falta repetir el ensayo en el equipo donde ocurrió el
cierre (`python cavidades/herramientas/ensayo_estres.py --ensayos 20`), con `registros/visor_*.log` activado, para obtener la
pila real.

Limitación: `QT_OPENGL=software` solo cambia el OpenGL de Qt; VTK usa el del controlador, así que no equivale a un renderizado por
software de VTK (p. ej. Mesa o el «Microsoft Basic Render Driver»).
