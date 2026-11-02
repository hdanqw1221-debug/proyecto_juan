"""Hoja de revisión de la forma de una o varias estructuras: seis vistas ortogonales y una oblicua.

Uso: python visor/hoja_organo.py OBJETO_ID [OBJETO_ID ...] [--modelos DIR] [--salida PNG] [--contexto ID,ID]
Las estructuras de --contexto se dibujan translúcidas para ver las relaciones topográficas.
Cada panel lleva el nombre de la vista y una flecha hacia craneal para orientarse.
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import vtk  # noqa: E402

from visor.escena import DIR_MODELOS, hex_a_rgb  # noqa: E402

# (título, dirección desde el centro hacia la cámara, vector arriba)
VISTAS_HOJA = [
    ("Lateral izquierda", (0, 1, 0), (0, 0, 1)),
    ("Lateral derecha", (0, -1, 0), (0, 0, 1)),
    ("Dorsal", (0, 0, 1), (1, 0, 0)),
    ("Ventral", (0, 0, -1), (1, 0, 0)),
    ("Craneal", (1, 0, 0), (0, 0, 1)),
    ("Caudal", (-1, 0, 0), (0, 0, 1)),
    ("Oblicua craneolateral izq.", (0.6, 0.6, 0.5), (0, 0, 1)),
    ("Oblicua caudomedial", (-0.6, -0.5, 0.45), (0, 0, 1)),
]


def _actor(dir_modelos, meta, opacidad=None):
    lector = vtk.vtkOBJReader()
    lector.SetFileName(str(Path(dir_modelos) / meta["archivo"]))
    normales = vtk.vtkPolyDataNormals()
    normales.SetInputConnection(lector.GetOutputPort())
    normales.SetFeatureAngle(80.0)
    mapper = vtk.vtkPolyDataMapper()
    mapper.SetInputConnection(normales.GetOutputPort())
    mapper.ScalarVisibilityOff()
    actor = vtk.vtkActor()
    actor.SetMapper(mapper)
    prop = actor.GetProperty()
    prop.SetColor(*hex_a_rgb(meta["color_hex"]))
    prop.SetOpacity(meta["opacidad"] if opacidad is None else opacidad)
    prop.SetSpecular(0.3)
    prop.SetSpecularPower(30)
    prop.SetInterpolationToPhong()
    return actor


def hoja(ids, dir_modelos=DIR_MODELOS, salida=None, contexto=(), tam_panel=520):
    dir_modelos = Path(dir_modelos)
    manifiesto = json.loads((dir_modelos / "manifiesto.json").read_text(encoding="utf-8"))
    por_id = {o["objeto_id"]: o for o in manifiesto["objetos"]}
    columnas, filas = 4, 2
    ventana = vtk.vtkRenderWindow()
    ventana.SetOffScreenRendering(1)
    ventana.SetSize(columnas * tam_panel, filas * tam_panel)
    principales = [_actor(dir_modelos, por_id[i], opacidad=1.0) for i in ids]
    caja = vtk.vtkBoundingBox()
    for a in principales:
        a.GetMapper().Update()
        caja.AddBounds(a.GetBounds())
    centro = [0.0, 0.0, 0.0]
    caja.GetCenter(centro)
    radio = 0.5 * caja.GetDiagonalLength()
    for k, (titulo, direccion, arriba) in enumerate(VISTAS_HOJA):
        ren = vtk.vtkRenderer()
        c, f = k % columnas, k // columnas
        ren.SetViewport(c / columnas, 1 - (f + 1) / filas, (c + 1) / columnas, 1 - f / filas)
        ren.SetBackground(0.13, 0.14, 0.16)
        for i in ids:
            ren.AddActor(_actor(dir_modelos, por_id[i], opacidad=1.0))
        for i in contexto:
            ren.AddActor(_actor(dir_modelos, por_id[i], opacidad=0.18))
        cam = ren.GetActiveCamera()
        d = vtk.vtkMath.Norm(direccion)
        cam.SetFocalPoint(*centro)
        cam.SetPosition(*(centro[j] + direccion[j] / d * radio * 4.0 for j in range(3)))
        cam.SetViewUp(*arriba)
        cam.SetViewAngle(30.0)
        cam.OrthogonalizeViewUp()
        ren.ResetCameraClippingRange()
        cam.Zoom(1.05)
        texto = vtk.vtkTextActor()
        texto.SetInput(titulo)
        texto.GetTextProperty().SetFontSize(18)
        texto.GetTextProperty().SetColor(0.9, 0.9, 0.9)
        texto.SetPosition(10, tam_panel - 30)
        ren.AddViewProp(texto)
        # Orientación de la vista: hacia dónde quedan craneal, dorsal e izquierda.
        flecha = vtk.vtkTextActor()
        flecha.GetTextProperty().SetFontSize(15)
        flecha.GetTextProperty().SetColor(1.0, 0.85, 0.3)
        flecha.SetPosition(10, 10)
        flecha.SetInput(_texto_orientacion(cam))
        ren.AddViewProp(flecha)
        ventana.AddRenderer(ren)
    ventana.Render()
    w2i = vtk.vtkWindowToImageFilter()
    w2i.SetInput(ventana)
    w2i.Update()
    salida = Path(salida or (dir_modelos / "vistas" / f"hoja_{'_'.join(ids)}.png"))
    salida.parent.mkdir(parents=True, exist_ok=True)
    png = vtk.vtkPNGWriter()
    png.SetFileName(str(salida))
    png.SetInputConnection(w2i.GetOutputPort())
    png.Write()
    ventana.Finalize()
    return salida


def _texto_orientacion(cam):
    """Indica hacia dónde quedan craneal y dorsal en la pantalla."""
    import numpy as np

    pos, foco = np.array(cam.GetPosition()), np.array(cam.GetFocalPoint())
    adelante = (foco - pos) / np.linalg.norm(foco - pos)
    arriba = np.array(cam.GetViewUp())
    derecha = np.cross(adelante, arriba)
    partes = []
    for nombre, eje in (("craneal", np.array([1.0, 0, 0])), ("dorsal", np.array([0, 0, 1.0])),
                        ("izq.", np.array([0, 1.0, 0]))):
        h, v = float(eje @ derecha), float(eje @ arriba)
        if max(abs(h), abs(v)) < 0.3:
            continue
        flecha = ("->" if h > 0 else "<-") if abs(h) >= abs(v) else ("arriba" if v > 0 else "abajo")
        partes.append(f"{nombre} {flecha}")
    return "   ".join(partes)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("ids", nargs="+")
    ap.add_argument("--modelos", default=str(DIR_MODELOS))
    ap.add_argument("--salida", default=None)
    ap.add_argument("--contexto", default="")
    ap.add_argument("--panel", type=int, default=520)
    a = ap.parse_args()
    print(hoja(a.ids, a.modelos, a.salida, [c for c in a.contexto.split(",") if c], a.panel))
