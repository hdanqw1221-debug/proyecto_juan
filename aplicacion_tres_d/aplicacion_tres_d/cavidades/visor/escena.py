"""Carga de la escena de cavidades en VTK a partir de `modelos/manifiesto.json`.

Compartido por el visor general, los modelos inmersivos por cavidad y las capturas
offscreen. Cada estructura es un actor independiente con su `objeto_id` asociado.
"""
import json
import zlib
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

# Aspecto de los tejidos en modo realista: (rugosidad PBR, amplitud del moteado).
# Las serosas (pleura, peritoneo) son húmedas y brillantes; el hígado y el bazo, más mates.
ACABADOS = {
    "respiratorio": (0.55, 0.10), "cardiovascular": (0.38, 0.08), "digestivo": (0.42, 0.09),
    "linfatico": (0.50, 0.10), "urinario": (0.45, 0.08), "endocrino": (0.50, 0.08),
    "reproductor": (0.40, 0.07), "limites": (0.35, 0.05), "referencia": (0.70, 0.0),
}


def hex_a_rgb(c):
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


def colores_organicos(polydata, color_hex, amplitud, semilla):
    """Colores por vértice con un moteado suave (suma de ondas en 3D) para un aspecto orgánico."""
    import numpy as np
    from vtk.util.numpy_support import numpy_to_vtk, vtk_to_numpy

    pts = vtk_to_numpy(polydata.GetPoints().GetData())
    rng = np.random.default_rng(semilla)
    ruido = np.zeros(len(pts))
    for frecuencia in (18.0, 37.0, 71.0, 140.0):
        for _ in range(3):
            d = rng.normal(size=3)
            d /= np.linalg.norm(d)
            ruido += np.sin(pts @ d * frecuencia + rng.uniform(0, 2 * np.pi)) * (18.0 / frecuencia) ** 0.6
    ruido /= np.max(np.abs(ruido)) + 1e-9
    base = np.array(hex_a_rgb(color_hex))
    rgb = base[None, :] * (1.0 + amplitud * ruido[:, None])
    rgb[:, 0] *= 1.0 + 0.3 * amplitud * np.clip(ruido, 0, None)  # vetas algo más rojizas
    arr = numpy_to_vtk(np.clip(rgb * 255.0, 0, 255).astype(np.uint8), deep=True)
    arr.SetName("color_tejido")
    polydata.GetPointData().SetScalars(arr)


class Escena:
    """Actores VTK de las estructuras del manifiesto.

    cavidades: limita la carga a esas cavidades (modelos inmersivos).
    realista: materiales PBR y moteado orgánico.
    mostrar_envolturas: las envolturas de cavidad empiezan visibles (modelo inmersivo).
    """

    def __init__(self, renderer, dir_modelos=DIR_MODELOS, decimar_si_supera=None, cavidades=None, realista=False,
                 mostrar_envolturas=False):
        self.renderer = renderer
        self.dir = Path(dir_modelos)
        self.realista = realista
        manifiesto = json.loads((self.dir / "manifiesto.json").read_text(encoding="utf-8"))
        self.meta = {}
        self.actores = {}
        self.id_de_actor = {}
        for o in manifiesto["objetos"]:
            if cavidades and o["cavidad"] not in cavidades:
                continue
            o.setdefault("envoltura", False)
            actor = self._crear_actor(o, decimar_si_supera)
            if o["envoltura"]:
                actor.SetVisibility(bool(mostrar_envolturas))
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
        datos = normales.GetOutput()
        mapper = vtk.vtkPolyDataMapper()
        mapper.SetInputData(datos)
        actor = vtk.vtkActor()
        actor.SetMapper(mapper)
        prop = actor.GetProperty()
        prop.SetColor(*hex_a_rgb(o["color_hex"]))
        prop.SetOpacity(o["opacidad"])
        rugosidad, amplitud = ACABADOS.get(o["sistema"], (0.5, 0.08))
        if self.realista:
            colores_organicos(datos, o["color_hex"], amplitud, zlib.crc32(o["objeto_id"].encode()))
            mapper.SetColorModeToDirectScalars()
            mapper.SetScalarModeToUsePointData()
            mapper.ScalarVisibilityOn()
            prop.SetInterpolationToPBR()
            prop.SetMetallic(0.0)
            prop.SetRoughness(rugosidad)
        else:
            mapper.ScalarVisibilityOff()
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
            actor.SetVisibility(m["cavidad"] in cavidades and not m["envoltura"]
                                and (incluir_ocultos or m["visible_por_defecto"]))

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


def configurar_render_realista(renderer, ventana):
    """Iluminación de tres puntos, oclusión ambiental (SSAO) y suavizado para el modo inmersivo."""
    renderer.RemoveAllLights()
    kit = vtk.vtkLightKit()
    kit.SetKeyLightIntensity(0.95)
    kit.AddLightsToRenderer(renderer)
    renderer.SetUseSSAO(True)
    renderer.SetSSAORadius(0.035)
    renderer.SetSSAOBias(0.002)
    renderer.SetSSAOKernelSize(32)
    renderer.SetSSAOBlur(True)
    renderer.SetUseFXAA(True)
