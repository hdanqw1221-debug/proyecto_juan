"""Capturas offscreen de las 5 vistas predefinidas para revisión anatómica.

Uso: python visor/capturas.py [--modelos DIR] [--cavidades toracica,abdominal] [--sufijo txt] [--ocultos]
Salida: <modelos>/vistas/<sufijo>_<vista>.png
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import vtk  # noqa: E402

from visor.escena import DIR_MODELOS, VISTAS, Escena  # noqa: E402


def capturar(dir_modelos, cavidades=None, sufijo="todo", incluir_ocultos=False, tam=(1200, 900)):
    ren = vtk.vtkRenderer()
    ren.SetBackground(0.16, 0.17, 0.19)
    ventana = vtk.vtkRenderWindow()
    ventana.SetOffScreenRendering(1)
    ventana.SetAlphaBitPlanes(1)
    ventana.SetMultiSamples(0)
    ren.SetUseDepthPeeling(1)  # transparencias correctas (diafragma, pericardio, conducto inguinal)
    ren.SetMaximumNumberOfPeels(8)
    ventana.AddRenderer(ren)
    ventana.SetSize(*tam)
    escena = Escena(ren, dir_modelos)
    if cavidades:
        escena.mostrar_solo(cavidades, incluir_ocultos)
    salida = Path(dir_modelos) / "vistas"
    salida.mkdir(parents=True, exist_ok=True)
    rutas = []
    for nombre in VISTAS:
        escena.aplicar_vista(nombre)
        ventana.Render()
        w2i = vtk.vtkWindowToImageFilter()
        w2i.SetInput(ventana)
        w2i.Update()
        png = vtk.vtkPNGWriter()
        ruta = salida / f"{sufijo}_{nombre}.png"
        png.SetFileName(str(ruta))
        png.SetInputConnection(w2i.GetOutputPort())
        png.Write()
        rutas.append(ruta)
    ventana.Finalize()
    return rutas


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--modelos", default=str(DIR_MODELOS))
    ap.add_argument("--cavidades", default="")
    ap.add_argument("--sufijo", default="todo")
    ap.add_argument("--ocultos", action="store_true")
    ap.add_argument("--ancho", type=int, default=1200)
    ap.add_argument("--alto", type=int, default=900)
    a = ap.parse_args()
    cav = [c for c in a.cavidades.split(",") if c]
    for r in capturar(a.modelos, cav or None, a.sufijo, a.ocultos, (a.ancho, a.alto)):
        print(r)
