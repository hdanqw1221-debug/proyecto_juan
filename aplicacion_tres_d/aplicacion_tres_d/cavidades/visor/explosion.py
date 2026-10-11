"""Despiece por proximidad de los modelos inmersivos.

Desde lejos se ve la cavidad como un todo (su envoltura). Al acercar la cámara
la envoltura se desvanece y los componentes se separan siguiendo vectores
anatómicos (pulmones hacia los lados, corazón ventral, diafragma caudal…),
con una etiqueta flotante sobre cada uno.
"""
import numpy as np
import vtk

# Desplazamientos máximos (m) definidos a mano donde el despiece radial no es claro.
DESPLAZAMIENTOS = {
    "toracica": {
        "TOR_PULMON_IZQ": (0.0, 0.42, 0.04), "TOR_PULMON_DER": (0.0, -0.42, 0.04),
        "TOR_CORAZON": (0.10, 0.06, -0.24), "TOR_PERICARDIO": (0.10, 0.06, -0.24),
        "TOR_TRAQUEA": (0.18, 0.0, 0.16), "TOR_ESOFAGO": (0.0, -0.05, 0.25),
        "TOR_AORTA": (0.0, 0.12, 0.36), "TOR_VENA_CAVA_CRANEAL": (0.24, -0.12, -0.06),
        "TOR_VENA_CAVA_CAUDAL": (-0.08, -0.18, -0.12), "TOR_DIAFRAGMA": (-0.40, 0.0, 0.0),
        "TOR_MEDIASTINO": (0.0, 0.0, 0.0),
    },
    "abdominal": {
        "ABD_HIGADO": (0.34, -0.10, 0.10), "ABD_ESTOMAGO": (0.28, 0.16, 0.02), "ABD_BAZO": (0.10, 0.36, 0.08),
        "ABD_AORTA": (0.0, 0.02, 0.34), "ABD_VENA_CAVA_CAUDAL": (0.04, -0.06, 0.32),
        "ABD_A_MESENTERICA_CRANEAL": (0.0, 0.0, 0.26), "ABD_PANCREAS": (0.12, -0.04, 0.28),
        "ABD_RINON_DER": (0.06, -0.22, 0.30), "ABD_RINON_IZQ": (-0.02, 0.22, 0.30),
        "ABD_ADRENAL_DER": (0.10, -0.18, 0.36), "ABD_ADRENAL_IZQ": (0.04, 0.18, 0.36),
        "ABD_URETER_DER": (-0.04, -0.16, 0.24), "ABD_URETER_IZQ": (-0.04, 0.16, 0.24),
    },
}
# Escala anisótropa del despiece radial para el resto (x, y, z) y desplazamiento extra por capa.
ESCALAS = {"toracica": (0.8, 1.4, 1.0), "abdominal": (0.9, 1.7, 1.2), "inguinal": (1.1, 2.2, 0.9)}
EXTRA_POR_CAPA_Z = {"toracica": 0.0, "abdominal": 0.07, "inguinal": 0.0}
# Inguinal: separaciones adicionales por tipo de estructura, en el sentido de su lado (y).
EXTRA_INGUINAL = {
    "ING_TESTICULO": (0.0, 0.05, -0.02), "ING_EPIDIDIMO": (0.0, 0.13, 0.0), "ING_CORDON_ESPERMATICO": (0.0, 0.08, 0.0),
    "ING_CANAL_INGUINAL": (0.0, 0.05, 0.05), "ING_ANILLO_PROFUNDO": (0.03, 0.05, 0.09),
    "ING_ANILLO_SUPERFICIAL": (-0.03, 0.05, 0.02), "ING_VASOS_TESTICULARES": (0.0, 0.03, 0.12),
    "ING_CONDUCTO_DEFERENTE": (0.0, -0.02, 0.08),
}

# Tramos del factor de proximidad t (0 lejos, 1 cerca).
FIN_DESVANECIDO = 0.40   # la envoltura desaparece entre t = 0 y este valor
INICIO_DESPIECE = 0.20   # los componentes empiezan a separarse
UMBRAL_ETIQUETAS = 0.35  # factor de despiece a partir del cual se muestran las etiquetas
RELACION_CERCA = 0.60    # distancia "cerca" respecto a la distancia de la vista completa
ANGULO_VISTA = (30.0, 58.0)  # el campo de visión se abre al separarse las piezas (inmersión)
PROFUNDIDAD_CONSERVADA = 0.25  # fracción del desplazamiento hacia/desde la cámara que se mantiene


def texto_etiqueta(nombre):
    """Nombre abreviado para la etiqueta flotante."""
    corto = nombre.split(" / ")[0].split(" (")[0]
    for largo, abrev in ((" izquierdo", " (izq.)"), (" izquierda", " (izq.)"), (" derecho", " (der.)"),
                         (" derecha", " (der.)")):
        if nombre.endswith(largo.strip()) or nombre.endswith(largo):
            corto = corto.replace(largo, "") + abrev
    return corto


def suave(x):
    x = float(np.clip(x, 0.0, 1.0))
    return x * x * (3.0 - 2.0 * x)


class ControladorExplosion:
    def __init__(self, renderer, escena, cavidad, etiquetas=True):
        self.renderer = renderer
        self.escena = escena
        self.cavidad = cavidad
        self.envoltura = next((oid for oid, m in escena.meta.items() if m["envoltura"]), None)
        self.automatico = True
        self.etiquetas_activas = etiquetas
        self.t = 0.0
        self.e = 0.0
        self.d_lejos = None
        self.d_cerca = None
        meta_env = escena.meta.get(self.envoltura)
        if meta_env:
            self.centro = np.array(meta_env["centroide"])
        else:
            b = escena.limites_visibles()
            self.centro = np.array([(b[0] + b[1]) / 2, (b[2] + b[3]) / 2, (b[4] + b[5]) / 2])
        self.desplazamiento = {oid: self._vector(oid, m) for oid, m in escena.meta.items() if not m["envoltura"]}
        self.en_pantalla = dict(self.desplazamiento)
        # Las etiquetas se dibujan en una capa superpuesta que comparte la cámara: nunca quedan tapadas.
        self.capa = renderer
        ventana = renderer.GetRenderWindow()
        if ventana is not None:
            ventana.SetNumberOfLayers(max(2, ventana.GetNumberOfLayers()))
            self.capa = vtk.vtkRenderer()
            self.capa.SetLayer(1)
            self.capa.InteractiveOff()
            self.capa.SetActiveCamera(renderer.GetActiveCamera())
            ventana.AddRenderer(self.capa)
        self.etiquetas = {}
        # Prioridad de las etiquetas: primero las estructuras más voluminosas.
        self.orden_etiquetas = sorted((oid for oid, m in escena.meta.items() if not m["envoltura"] and m["seleccionable"]),
                                      key=lambda o: -escena.meta[o]["volumen_m3"])
        for oid in self.orden_etiquetas:
            m = escena.meta[oid]
            et = vtk.vtkBillboardTextActor3D()
            et.SetInput(texto_etiqueta(m["nombre"]))
            tp = et.GetTextProperty()
            tp.SetFontSize(15)
            tp.SetColor(1.0, 1.0, 1.0)
            tp.SetBackgroundColor(0.08, 0.09, 0.11)
            tp.SetBackgroundOpacity(0.55)
            tp.SetJustificationToCentered()
            et.SetPickable(False)
            et.SetVisibility(False)
            self.capa.AddActor(et)
            self.etiquetas[oid] = et

    def _vector(self, oid, m):
        fijo = DESPLAZAMIENTOS.get(self.cavidad, {}).get(oid)
        if fijo is not None:
            return np.array(fijo, dtype=float)
        rel = np.array(m["centroide"]) - self.centro
        v = rel * np.array(ESCALAS.get(self.cavidad, (1.0, 1.0, 1.0)))
        v[2] += EXTRA_POR_CAPA_Z.get(self.cavidad, 0.0) * (m["capa"] - 1.5)
        if self.cavidad == "inguinal":
            base = oid.rsplit("_", 1)[0]
            ex = np.array(EXTRA_INGUINAL.get(base, (0.0, 0.0, 0.0)))
            lado = 1.0 if m["lado"] == "IZQ" else -1.0 if m["lado"] == "DER" else 0.0
            v += np.array([ex[0], ex[1] * lado, ex[2]])
        return v

    # ------------------------------------------------------------------ cámara
    def calibrar(self):
        """Toma la distancia actual de la cámara como la vista completa (cavidad ensamblada)."""
        cam = self.renderer.GetActiveCamera()
        self.d_lejos = cam.GetDistance()
        self.d_cerca = RELACION_CERCA * self.d_lejos

    def factor_proximidad(self):
        if not self.d_lejos:
            return 0.0
        d = self.renderer.GetActiveCamera().GetDistance()
        return float(np.clip((self.d_lejos - d) / (self.d_lejos - self.d_cerca), 0.0, 1.0))

    def actualizar(self):
        """Recalcula el despiece a partir de la distancia de la cámara (modo automático)."""
        if self.automatico:
            self.aplicar(self.factor_proximidad())

    # ------------------------------------------------------------------ despiece
    def aplicar(self, t):
        self.t = float(np.clip(t, 0.0, 1.0))
        a = suave(self.t / FIN_DESVANECIDO)
        if self.envoltura:
            actor = self.escena.actores[self.envoltura]
            base = self.escena.meta[self.envoltura]["opacidad"]
            opacidad = base * (1.0 - a)
            actor.GetProperty().SetOpacity(max(opacidad, 0.0))
            actor.SetVisibility(opacidad > 0.02)
            actor.SetPickable(opacidad > 0.5)  # desde lejos el clic informa sobre la cavidad completa
        self.e = suave((self.t - INICIO_DESPIECE) / (1.0 - INICIO_DESPIECE))
        self._vectores_pantalla()
        # Al separarse las piezas ocupan más que la cavidad ensamblada: se abre el campo de visión.
        self.renderer.GetActiveCamera().SetViewAngle(ANGULO_VISTA[0] + (ANGULO_VISTA[1] - ANGULO_VISTA[0]) * self.e)
        for oid, v in self.en_pantalla.items():
            self.escena.actores[oid].SetPosition(*(v * self.e))
        self._colocar_etiquetas()

    def _colocar_etiquetas(self):
        """Muestra las etiquetas que caben sin solaparse en pantalla, empezando por las piezas más grandes."""
        ocupadas = []
        opacidad = min(1.0, (self.e - UMBRAL_ETIQUETAS) / 0.25)
        for oid in self.orden_etiquetas:
            et = self.etiquetas[oid]
            visible = self.etiquetas_activas and self.e > UMBRAL_ETIQUETAS and self.escena.actores[oid].GetVisibility()
            if visible:
                m = self.escena.meta[oid]
                p = np.array(m["centroide"]) + self.en_pantalla[oid] * self.e
                self.renderer.SetWorldPoint(p[0], p[1], p[2], 1.0)
                self.renderer.WorldToDisplay()
                x, y, _ = self.renderer.GetDisplayPoint()
                ancho = 7.5 * len(et.GetInput()) + 10
                caja = (x - ancho / 2, y - 11, x + ancho / 2, y + 11)
                if any(caja[0] < c[2] and c[0] < caja[2] and caja[1] < c[3] and c[1] < caja[3] for c in ocupadas):
                    visible = False
                else:
                    ocupadas.append(caja)
                    et.SetPosition(*p)
                    et.GetTextProperty().SetOpacity(opacidad)
            et.SetVisibility(visible)

    def _vectores_pantalla(self):
        """Reorienta los desplazamientos hacia el plano de la pantalla.

        Una pieza que se separara hacia la cámara taparía al resto: la componente según la
        dirección de visión se gira dentro del plano (lo que viene hacia el observador sube y lo
        que se aleja baja, siguiendo su propio sentido en pantalla si lo tiene).
        """
        cam = self.renderer.GetActiveCamera()
        vista = np.array(cam.GetPosition()) - np.array(cam.GetFocalPoint())
        vista /= np.linalg.norm(vista) + 1e-12
        arriba = np.array(cam.GetViewUp())
        arriba = arriba - np.dot(arriba, vista) * vista
        arriba /= np.linalg.norm(arriba) + 1e-12
        self.en_pantalla = {}
        for oid, v in self.desplazamiento.items():
            hacia = float(np.dot(v, vista))
            plano = v - hacia * vista
            n = np.linalg.norm(plano)
            sentido = plano / n if n > 0.2 * abs(hacia) and n > 1e-6 else arriba
            girado = abs(hacia) * (1.0 - PROFUNDIDAD_CONSERVADA) * (sentido if hacia >= 0 else -sentido)
            self.en_pantalla[oid] = plano + girado + PROFUNDIDAD_CONSERVADA * hacia * vista

    def posicion_actual(self, oid):
        """Centroide desplazado de una estructura (para encuadrarla)."""
        m = self.escena.meta[oid]
        return np.array(m["centroide"]) + self.en_pantalla.get(oid, np.zeros(3)) * self.e
