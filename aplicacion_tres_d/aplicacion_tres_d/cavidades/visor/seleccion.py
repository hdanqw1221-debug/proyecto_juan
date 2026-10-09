"""Selección por clic, profundización a lo largo del rayo y resaltado (sin dependencias de Qt)."""
import vtk

from visor.escena import hex_a_rgb

COLOR_SELECCION = hex_a_rgb("#FFD54F")
UMBRAL_ARRASTRE_PX = 5
UMBRAL_REPETICION_PX = 6
MAX_CAPAS = 12


class Seleccionador:
    """Convierte clics de pantalla en estructuras y gestiona el clic repetido para profundizar."""

    def __init__(self, renderer, escena):
        self.renderer = renderer
        self.escena = escena
        self.picker = vtk.vtkCellPicker()
        self.picker.SetTolerance(0.0005)
        # Un localizador de celdas por malla acelera el pick (cientos de miles de triángulos).
        for actor in escena.actores.values():
            loc = vtk.vtkStaticCellLocator()
            loc.SetDataSet(actor.GetMapper().GetInput())
            loc.BuildLocator()
            self.picker.AddLocator(loc)
        self._ultimo_punto = None
        self._pila = []
        self._indice = -1
        self._agotada = False

    def _siguiente(self, x, y, excluidos):
        """Primera estructura bajo el píxel sin contar `excluidos` (objeto_id) o None."""
        desactivados = []
        try:
            for oid in excluidos:
                actor = self.escena.actores[oid]
                if actor.GetPickable():
                    actor.PickableOff()
                    desactivados.append(actor)
            if not self.picker.Pick(x, y, 0, self.renderer):
                return None
            return self.escena.id_de_actor.get(self.picker.GetActor())
        finally:
            for actor in desactivados:
                actor.PickableOn()

    def estructuras_en(self, x, y):
        """objeto_id de todas las estructuras visibles y seleccionables bajo el píxel, de la más cercana a la más profunda."""
        pila = []
        for _ in range(MAX_CAPAS):
            oid = self._siguiente(x, y, pila)
            if oid is None:
                break
            pila.append(oid)
        return pila

    def clic(self, x, y):
        """Devuelve el objeto_id a seleccionar. Un clic repetido en el mismo punto avanza a la capa siguiente.

        Cada clic hace una sola selección por rayo (la pila se amplía de forma perezosa).
        """
        repetido = (self._ultimo_punto is not None and self._pila
                    and abs(x - self._ultimo_punto[0]) <= UMBRAL_REPETICION_PX
                    and abs(y - self._ultimo_punto[1]) <= UMBRAL_REPETICION_PX)
        if repetido:
            x, y = self._ultimo_punto
            siguiente = None if self._agotada or len(self._pila) >= MAX_CAPAS else self._siguiente(x, y, self._pila)
            if siguiente is None:
                self._agotada = True
                self._indice = (self._indice + 1) % len(self._pila)
            else:
                self._pila.append(siguiente)
                self._indice = len(self._pila) - 1
        else:
            primero = self._siguiente(x, y, [])
            self._pila = [primero] if primero else []
            self._indice = 0
            self._agotada = False
            self._ultimo_punto = (x, y)
        if not self._pila:
            self._ultimo_punto = None
            return None
        return self._pila[self._indice]

    def profundidad_actual(self):
        """(capa actual, capas conocidas); el total crece al profundizar."""
        return (self._indice + 1, len(self._pila)) if self._pila else (0, 0)

    def reiniciar(self):
        self._ultimo_punto, self._pila, self._indice, self._agotada = None, [], -1, False


class Resaltador:
    """Resalta un actor (color, luz ambiente y silueta) y restaura su aspecto al deseleccionar."""

    def __init__(self, renderer):
        self.renderer = renderer
        self.actor = None
        self._previo = None
        self._silueta = None

    def resaltar(self, actor):
        self.quitar()
        prop = actor.GetProperty()
        self._previo = (prop.GetColor(), prop.GetAmbient(), prop.GetOpacity())
        prop.SetColor(*COLOR_SELECCION)
        prop.SetAmbient(0.45)
        prop.SetOpacity(max(prop.GetOpacity(), 0.85))
        sil = vtk.vtkPolyDataSilhouette()
        sil.SetInputData(actor.GetMapper().GetInput())
        sil.SetCamera(self.renderer.GetActiveCamera())
        sil.SetEnableFeatureAngle(0)
        mapper = vtk.vtkPolyDataMapper()
        mapper.SetInputConnection(sil.GetOutputPort())
        self._silueta = vtk.vtkActor()
        self._silueta.SetMapper(mapper)
        self._silueta.GetProperty().SetColor(0.1, 0.1, 0.1)
        self._silueta.GetProperty().SetLineWidth(3.0)
        self._silueta.PickableOff()
        self.renderer.AddActor(self._silueta)
        self.actor = actor

    def quitar(self):
        if self.actor is not None and self._previo is not None:
            prop = self.actor.GetProperty()
            color, ambiente, opacidad = self._previo
            prop.SetColor(*color)
            prop.SetAmbient(ambiente)
            prop.SetOpacity(opacidad)
        if self._silueta is not None:
            self.renderer.RemoveActor(self._silueta)
        self.actor, self._previo, self._silueta = None, None, None
