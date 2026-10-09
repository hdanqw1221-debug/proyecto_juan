"""Carga de la escena de cavidades en VTK a partir de `modelos/manifiesto.json`.

Compartido por el visor interactivo y por las capturas offscreen. Cada
estructura es un actor independiente con su `objeto_id` asociado.
"""
import json
from pathlib import Path

import vtk

DIR_MODELOS = Path(__file__).resolve().parents[1] / "modelos"

# Vistas predefinidas: dirección desde el centro hacia la cámara y vector "arriba".
VISTAS = {
    "lateral_izquierda": ((0.0, 1.0, 0.0), (0.0, 0.0, 1.0)),
    "lateral_derecha": ((0.0, -1.0, 0.0), (0.0, 0.0, 1.0)),
    "ventral": ((0.0, 0.0, -1.0), (1.0, 0.0, 0.0)),
    "dorsal": ((0.0, 0.0, 1.0), (1.0, 0.0, 0.0)),
    "craneal": ((1.0, 0.0, 0.0), (0.0, 0.0, 1.0)),
}


def hex_a_rgb(c):
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


class Escena:
    """Actores VTK de todas las estructuras del manifiesto."""

    def __init__(self, renderer, dir_modelos=DIR_MODELOS, decimar_si_supera=None):
        self.renderer = renderer
        self.dir = Path(dir_modelos)
        manifiesto = json.loads((self.dir / "manifiesto.json").read_text(encoding="utf-8"))
        self.meta = {}
        self.actores = {}
        self.id_de_actor = {}
        for o in manifiesto["objetos"]:
            actor = self._crear_actor(o, decimar_si_supera)
            self.meta[o["objeto_id"]] = o
            self.actores[o["objeto_id"]] = actor
            self.id_de_actor[actor] = o["objeto_id"]
            renderer.AddActor(actor)

    def _crear_actor(self, o, decimar_si_supera):
        lector = vtk.vtkOBJReader()
        lector.SetFileName(str(self.dir / o["archivo"]))
        fuente = lector
        if decimar_si_supera and o["n_triangulos"] > decimar_si_supera:
            dec = vtk.vtkQuadricDecimation()
            dec.SetInputConnection(lector.GetOutputPort())
            dec.SetTargetReduction(1.0 - decimar_si_supera / o["n_triangulos"])
            fuente = dec
        normales = vtk.vtkPolyDataNormals()
        normales.SetInputConnection(fuente.GetOutputPort())
        normales.SetFeatureAngle(80.0)
        normales.Update()
        mapper = vtk.vtkPolyDataMapper()
        mapper.SetInputData(normales.GetOutput())
        actor = vtk.vtkActor()
        actor.SetMapper(mapper)
        prop = actor.GetProperty()
        prop.SetColor(*hex_a_rgb(o["color_hex"]))
        prop.SetOpacity(o["opacidad"])
        prop.SetSpecular(0.25)
        prop.SetSpecularPower(25)
        prop.SetInterpolationToPhong()
        if o["opacidad"] < 1.0:
            prop.BackfaceCullingOff()
        actor.SetVisibility(bool(o["visible_por_defecto"]))
        actor.SetPickable(bool(o["seleccionable"]))
        return actor

    def mostrar_solo(self, cavidades, incluir_ocultos=False):
        for oid, actor in self.actores.items():
            m = self.meta[oid]
            actor.SetVisibility(m["cavidad"] in cavidades and (incluir_ocultos or m["visible_por_defecto"]))

    def limites_visibles(self):
        caja = [1e9, -1e9, 1e9, -1e9, 1e9, -1e9]
        for actor in self.actores.values():
            if actor.GetVisibility():
                b = actor.GetBounds()
                for k in range(3):
                    caja[2 * k] = min(caja[2 * k], b[2 * k])
                    caja[2 * k + 1] = max(caja[2 * k + 1], b[2 * k + 1])
        return caja if caja[0] < caja[1] else [0, 1.6, -0.3, 0.3, 0, 0.7]

    def aplicar_vista(self, nombre, camara=None):
        camara = camara or self.renderer.GetActiveCamera()
        direccion, arriba = VISTAS[nombre]
        b = self.limites_visibles()
        centro = [(b[0] + b[1]) / 2, (b[2] + b[3]) / 2, (b[4] + b[5]) / 2]
        tam = max(b[1] - b[0], b[3] - b[2], b[5] - b[4])
        distancia = 2.4 * tam
        camara.SetFocalPoint(*centro)
        camara.SetPosition(*(centro[k] + direccion[k] * distancia for k in range(3)))
        camara.SetViewUp(*arriba)
        camara.SetViewAngle(30.0)
        self.renderer.ResetCameraClippingRange()
