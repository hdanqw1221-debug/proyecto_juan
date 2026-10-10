"""Capturas del despiece de cada modelo inmersivo (lejos, media distancia y cerca).

Uso: python visor/capturas_inmersivas.py [--modelos DIR]
Salida: <modelos>/vistas/inmersivo_<cavidad>_<lejos|medio|cerca>.png
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
import vtk  # noqa: E402

from visor.escena import DIR_MODELOS, Escena, configurar_render_realista  # noqa: E402
from visor.explosion import ControladorExplosion  # noqa: E402
from visor.visor_cavidades import MODELOS_INMERSIVOS, VISTA_INICIAL  # noqa: E402

DISTANCIAS = {"lejos": 1.0, "medio": 0.8, "cerca": 0.6}


def capturar(dir_modelos, cavidad, tam=(1200, 900)):
    ren = vtk.vtkRenderer()
    ren.SetBackground(0.10, 0.11, 0.13)
    ren.SetBackground2(0.27, 0.29, 0.33)
    ren.GradientBackgroundOn()
    ren.SetUseDepthPeeling(1)
    ren.SetMaximumNumberOfPeels(8)
    ventana = vtk.vtkRenderWindow()
    ventana.SetOffScreenRendering(1)
    ventana.SetAlphaBitPlanes(1)
    ventana.SetMultiSamples(0)
    ventana.SetSize(*tam)
    ventana.AddRenderer(ren)
    escena = Escena(ren, dir_modelos, cavidades=MODELOS_INMERSIVOS[cavidad][1], realista=True, mostrar_envolturas=True)
    configurar_render_realista(ren, ventana)
    ctl = ControladorExplosion(ren, escena, cavidad)
    ctl.aplicar(0.0)
    escena.aplicar_vista(VISTA_INICIAL[cavidad])
    if cavidad == "inguinal":
        ren.GetActiveCamera().Elevation(-25)
        ren.GetActiveCamera().OrthogonalizeViewUp()
    ctl.calibrar()
    cam = ren.GetActiveCamera()
    salida = Path(dir_modelos) / "vistas"
    salida.mkdir(parents=True, exist_ok=True)
    rutas = []
    for nombre, factor in DISTANCIAS.items():
        f, p = np.array(cam.GetFocalPoint()), np.array(cam.GetPosition())
        cam.SetPosition(*(f + (p - f) / np.linalg.norm(p - f) * ctl.d_lejos * factor))
        ctl.actualizar()
        ren.ResetCameraClippingRange()
        ventana.Render()
        w2i = vtk.vtkWindowToImageFilter()
        w2i.SetInput(ventana)
        w2i.Update()
        png = vtk.vtkPNGWriter()
        ruta = salida / f"inmersivo_{cavidad}_{nombre}.png"
        png.SetFileName(str(ruta))
        png.SetInputConnection(w2i.GetOutputPort())
        png.Write()
        rutas.append(ruta)
    ventana.Finalize()
    return rutas


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--modelos", default=str(DIR_MODELOS))
    a = ap.parse_args()
    for cav in MODELOS_INMERSIVOS:
        for r in capturar(a.modelos, cav):
            print(r)
