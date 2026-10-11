"""Visor de las cavidades torácica, abdominal e inguinal del caballo (PyQt5 + VTK).

Dos modos:
- General (`cavidad=None`): todas las estructuras, con conmutadores por cavidad.
- Inmersivo por cavidad (`cavidad="toracica" | "abdominal" | "inguinal"`): desde lejos se ve la
  cavidad completa; al acercarse con la rueda del ratón se desvanece su envoltura y los órganos se
  separan con etiquetas. El doble clic (o la tecla F) encuadra la pieza seleccionada.

En ambos: clic (sin arrastre) para seleccionar y ver la ficha completa; clic repetido para la
estructura siguiente a lo largo del rayo; eventos JSON por la salida estándar para la app Java.
"""
import html
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
import vtk  # noqa: E402
from PyQt5.QtCore import Qt, QStringListModel, QTimer  # noqa: E402
from PyQt5.QtWidgets import (QApplication, QCheckBox, QCompleter, QGridLayout, QGroupBox, QLabel,  # noqa: E402
                             QLineEdit, QMainWindow, QPushButton, QSlider, QSplitter, QTextBrowser, QVBoxLayout,
                             QWidget)
from vtkmodules.qt.QVTKRenderWindowInteractor import QVTKRenderWindowInteractor  # noqa: E402

from visor.escena import DIR_MODELOS, Escena, configurar_render_realista  # noqa: E402
from visor.explosion import (ANGULO_VISTA, DURACION_TRANSICION, FACTOR_RUEDA, FIN_DESVANECIDO,  # noqa: E402
                             ControladorExplosion, acercar_hacia, punto_bajo_cursor)
from visor.repositorio_fichas import RepositorioFichas  # noqa: E402
from visor.seleccion import UMBRAL_ARRASTRE_PX, Resaltador, Seleccionador  # noqa: E402

CAVIDADES_TECLA = {"1": "toracica", "2": "abdominal", "3": "inguinal", "4": "pelvica", "5": "referencia"}
NOMBRES_CAVIDAD = {"toracica": "Torácica", "abdominal": "Abdominal", "inguinal": "Región inguinal",
                   "pelvica": "Pélvica (ref.)", "referencia": "Referencias"}
# Qué se carga en cada modelo inmersivo (la transición pélvica acompaña al abdomen).
MODELOS_INMERSIVOS = {
    "toracica": ("Cavidad torácica", ["toracica"]),
    "abdominal": ("Cavidad abdominal", ["abdominal", "pelvica"]),
    "inguinal": ("Región inguinal", ["inguinal"]),
}
VISTA_INICIAL = {None: "lateral_izquierda", "toracica": "lateral_izquierda", "abdominal": "lateral_izquierda",
                 "inguinal": "craneal"}
VISTAS_TECLA = {"F1": "lateral_izquierda", "F2": "lateral_derecha", "F3": "ventral", "F4": "dorsal", "F5": "craneal"}
ETIQUETAS_VISTA = {"lateral_izquierda": "Lateral izq.", "lateral_derecha": "Lateral der.", "ventral": "Ventral",
                   "dorsal": "Dorsal", "craneal": "Craneal"}
CAMPOS_FICHA = [
    ("descripcion", "Descripción"), ("topografia", "Topografía"), ("proyeccion_superficial", "Proyección superficial"),
    ("relaciones", "Relaciones"), ("medios_de_fijacion", "Medios de fijación"),
    ("irrigacion_drenaje", "Irrigación y drenaje"), ("dimensiones", "Dimensiones"),
    ("consideraciones_clinicas", "Consideraciones clínicas"), ("exploracion", "Exploración"),
]
NOMBRE_LADO = {"IZQ": "izquierdo", "DER": "derecho", "MED": "medio / impar"}
NOMBRE_CAPA = {0: "superficial (límite)", 1: "superficial", 2: "intermedia", 3: "profunda"}
OPACIDAD_TRANSLUCIDA = 0.15
DOBLE_CLIC_S = 0.35
LEYENDA_GENERAL = ("<b>Atajos</b><br>Rueda del ratón: acercarse hacia el cursor<br>"
                   "Clic: seleccionar · clic repetido: estructura más profunda<br>"
                   "1-5: Torácica / Abdominal / Inguinal / Pélvica / Referencias<br>"
                   "H: ocultar selección · A: mostrar todo · T: resto translúcido<br>"
                   "R: restablecer cámara · Esc: deseleccionar · F1-F5: vistas")
LEYENDA_INMERSIVA = ("<b>Cómo explorar</b><br>Rueda del ratón: acercarse hacia el cursor. Al acercarse, la "
                     "cavidad se separa en sus componentes, que quedan fijos para poder acercarse a cualquiera; "
                     "al alejarse se vuelve a ensamblar.<br>Clic: ficha completa · clic repetido: "
                     "estructura más profunda · doble clic o F: encuadrar la pieza<br>"
                     "L: etiquetas · T: resto translúcido · H: ocultar · A: mostrar todo · R: vista inicial · "
                     "Esc: deseleccionar · F1-F5: vistas")


def emitir(evento, **datos):
    """Evento para la aplicación Java: una línea JSON por la salida estándar."""
    print(json.dumps({"evento": evento, "t": round(time.time(), 3), **datos}, ensure_ascii=False), flush=True)


def _medidas(meta):
    caja = np.array(meta["caja"])
    dims = (caja[1] - caja[0]) * 100.0
    return (f"{dims[0]:.0f} × {dims[1]:.0f} × {dims[2]:.0f} cm (craneocaudal × transversal × dorsoventral); "
            f"volumen del modelo ≈ {meta['volumen_m3'] * 1000:.2f} L")


def html_ficha(ficha, meta, profundidad, relacionados=()):
    """Ficha completa de la estructura: todos los campos anatómicos, estado de validación y datos del modelo."""
    if ficha is None:
        return f"<h3>{html.escape(meta['nombre'])}</h3><p><i>Sin ficha anatómica.</i></p>"
    pend = set(ficha.get("campos_pendientes") or [])
    partes = [f"<h2 style='margin-bottom:0'>{html.escape(meta['nombre'])}</h2>",
              f"<p style='margin-top:2px'><i>{html.escape(ficha['nombre_latino'])}</i><br>"
              f"<small>{html.escape(ficha['nombre'])} · {html.escape(ficha['cavidad'])} · "
              f"{html.escape(ficha['sistema'])}</small></p>"]
    if ficha.get("pendiente_validacion"):
        partes.append("<p style='background:#FFE082;color:#5D4037;padding:4px'><b>Pendiente de validación</b>"
                      " por un anatomista veterinario.</p>")
    if profundidad[1] > 1:
        partes.append(f"<p><small>Capa {profundidad[0]} de {profundidad[1]} bajo el cursor "
                      "(clic de nuevo para profundizar).</small></p>")
    for campo, titulo in CAMPOS_FICHA:
        marca = " <small style='color:#B26A00'>(pendiente)</small>" if campo in pend else ""
        partes.append(f"<p><b>{titulo}{marca}:</b> {html.escape(str(ficha.get(campo, 'No aplica')))}</p>")
    partes.append("<p><b>Referencias:</b> " + html.escape("; ".join(ficha.get("referencias", []))) + "</p>")
    datos = [("Identificador", meta["objeto_id"]), ("Ficha", ficha["ficha_id"]),
             ("Lado", NOMBRE_LADO.get(meta["lado"], meta["lado"])),
             ("Capa", NOMBRE_CAPA.get(meta["capa"], str(meta["capa"]))),
             ("Sexo aplicable", ficha.get("sexo_aplicable", meta.get("sexo_aplicable", "ambos"))),
             ("Medidas en el modelo", _medidas(meta))]
    if relacionados:
        datos.append(("Comparte ficha con", ", ".join(relacionados)))
    filas = "".join(f"<tr><td><b>{html.escape(k)}</b></td><td>{html.escape(str(v))}</td></tr>" for k, v in datos)
    partes.append("<h4 style='margin-bottom:2px'>Datos del modelo 3D</h4>"
                  f"<table cellspacing='2' style='font-size:small'>{filas}</table>"
                  "<p><small>Las medidas son las del modelo esquemático; las reales figuran en «Dimensiones».</small></p>")
    return "\n".join(partes)


class EstiloInteraccion(vtk.vtkInteractorStyleTrackballCamera):
    """Trackball con distinción clic/arrastre, doble clic y rueda hacia el cursor; las teclas las gestiona el visor."""

    def __init__(self, visor):
        super().__init__()
        self.visor = visor
        self._inicio = None
        self._girando = False
        self._ultimo_clic = (0.0, None)
        self.AddObserver("LeftButtonPressEvent", self._presionar)
        self.AddObserver("LeftButtonReleaseEvent", self._soltar)
        self.AddObserver("MouseMoveEvent", self._mover)
        self.AddObserver("MouseWheelForwardEvent", lambda o, e: self._rueda(1))
        self.AddObserver("MouseWheelBackwardEvent", lambda o, e: self._rueda(-1))
        self.AddObserver("CharEvent", lambda o, e: None)  # anula los atajos por defecto de VTK (q, e, w, 3…)
        self.AddObserver("KeyPressEvent", self._tecla)

    def _presionar(self, obj, evento):
        # El giro empieza al arrastrar: un clic no mueve la cámara (ni, por tanto, el despiece).
        self._inicio = self.GetInteractor().GetEventPosition()
        self._girando = False

    def _mover(self, obj, evento):
        if self._inicio and not self._girando:
            x, y = self.GetInteractor().GetEventPosition()
            if max(abs(x - self._inicio[0]), abs(y - self._inicio[1])) >= UMBRAL_ARRASTRE_PX:
                self._girando = True
                self.OnLeftButtonDown()
        self.OnMouseMove()

    def _rueda(self, sentido):
        self.visor.rueda(*self.GetInteractor().GetEventPosition(), sentido)

    def _soltar(self, obj, evento):
        fin = self.GetInteractor().GetEventPosition()
        if self._girando:
            self.OnLeftButtonUp()
        elif self._inicio:
            ahora = time.time()
            t_prev, p_prev = self._ultimo_clic
            if p_prev and ahora - t_prev < DOBLE_CLIC_S and abs(fin[0] - p_prev[0]) + abs(fin[1] - p_prev[1]) < 8:
                self.visor.encuadrar_seleccion()
                self._ultimo_clic = (0.0, None)
            else:
                self.visor.clic(*fin)
                self._ultimo_clic = (ahora, fin)
        self._inicio = None
        self._girando = False

    def _tecla(self, obj, evento):
        self.visor.tecla(self.GetInteractor().GetKeySym())


class VisorCavidades(QMainWindow):
    def __init__(self, dir_modelos=DIR_MODELOS, cavidad=None):
        super().__init__()
        t0 = time.time()
        self.cavidad = cavidad
        self.inmersivo = cavidad is not None
        titulo, cargar = MODELOS_INMERSIVOS[cavidad] if self.inmersivo else ("Cavidades", None)
        self.setWindowTitle(f"Simulador 3D del equino - {titulo}" if self.inmersivo else
                            "Simulador 3D - Cavidades torácica, abdominal e inguinal del equino")
        self.resize(1400, 860)
        self.vtk_widget = QVTKRenderWindowInteractor(self)
        self.ventana = self.vtk_widget.GetRenderWindow()
        self.ventana.SetAlphaBitPlanes(1)
        self.ventana.SetMultiSamples(0)
        self.renderer = vtk.vtkRenderer()
        self.renderer.SetBackground(0.10, 0.11, 0.13)
        self.renderer.SetBackground2(0.27, 0.29, 0.33)
        self.renderer.GradientBackgroundOn()
        self.renderer.SetUseDepthPeeling(1)
        self.renderer.SetMaximumNumberOfPeels(8)
        self.ventana.AddRenderer(self.renderer)
        self.escena = Escena(self.renderer, dir_modelos, cavidades=cargar, realista=self.inmersivo,
                             mostrar_envolturas=self.inmersivo)
        if self.inmersivo:
            configurar_render_realista(self.renderer, self.ventana)
        self.repositorio = RepositorioFichas()
        self.seleccionador = Seleccionador(self.renderer, self.escena)
        self.resaltador = Resaltador(self.renderer)
        self.explosion = ControladorExplosion(self.renderer, self.escena, cavidad) if self.inmersivo else None
        self.seleccion = None
        self.translucido = False
        self.cavidades_visibles = {c: True for c in CAVIDADES_TECLA.values()}
        self.cavidades_visibles["referencia"] = False
        self._construir_panel()
        self.interactor = self.ventana.GetInteractor()
        self.interactor.SetInteractorStyle(EstiloInteraccion(self))
        self._actualizando = False
        # Transición animada entre la cavidad ensamblada y separada.
        self.temporizador = QTimer(self)
        self.temporizador.setInterval(15)
        self.temporizador.timeout.connect(self._paso_transicion)
        self._seguir = None
        self._t_previo = 0.0
        self.vista(VISTA_INICIAL[cavidad])
        if self.explosion:
            self.renderer.GetActiveCamera().AddObserver("ModifiedEvent", self._camara_cambiada)
        self.tiempo_carga = time.time() - t0
        self.inicio = time.time()
        self.estado.setText(f"{len(self.escena.actores)} estructuras · fichas desde {self.repositorio.origen} · "
                            f"carga {self.tiempo_carga:.1f} s")
        emitir("inicio", modelo=cavidad or "general", objetos=len(self.escena.actores),
               fuente_fichas=self.repositorio.origen, carga_s=round(self.tiempo_carga, 2))

    # ------------------------------------------------------------------ interfaz
    def _construir_panel(self):
        panel = QWidget()
        lay = QVBoxLayout(panel)
        self.buscador = QLineEdit()
        self.buscador.setPlaceholderText("Buscar estructura por nombre…")
        nombres = sorted(m["nombre"] for m in self.escena.meta.values() if m["seleccionable"])
        completar = QCompleter()
        completar.setModel(QStringListModel(nombres))
        completar.setCaseSensitivity(Qt.CaseInsensitive)
        completar.setFilterMode(Qt.MatchContains)
        completar.activated.connect(self.buscar)
        self.buscador.setCompleter(completar)
        self.buscador.returnPressed.connect(lambda: self.buscar(self.buscador.text()))
        lay.addWidget(self.buscador)
        self.ficha = QTextBrowser()
        self.ficha.setOpenExternalLinks(False)
        self.ficha.setHtml(self._texto_inicial())
        lay.addWidget(self.ficha, 1)
        self.checks = {}
        if self.inmersivo:
            caja = QGroupBox("Despiece de la cavidad")
            g = QVBoxLayout(caja)
            self.auto = QCheckBox("Separar al acercar la cámara (automático)")
            self.auto.setChecked(True)
            self.auto.stateChanged.connect(self._cambiar_automatico)
            self.deslizador = QSlider(Qt.Horizontal)
            self.deslizador.setRange(0, 100)
            self.deslizador.setEnabled(False)
            self.deslizador.valueChanged.connect(self._deslizador_movido)
            self.etiq = QCheckBox("Mostrar etiquetas (L)")
            self.etiq.setChecked(True)
            self.etiq.stateChanged.connect(self._cambiar_etiquetas)
            self.indicador = QLabel("Separación: 0 %")
            for w in (self.auto, self.deslizador, self.indicador, self.etiq):
                g.addWidget(w)
            lay.addWidget(caja)
        else:
            caja = QGroupBox("Cavidades")
            g = QGridLayout(caja)
            for i, (tecla, cav) in enumerate(CAVIDADES_TECLA.items()):
                cb = QCheckBox(f"{tecla} {NOMBRES_CAVIDAD[cav]}")
                cb.setChecked(self.cavidades_visibles[cav])
                cb.stateChanged.connect(lambda estado, c=cav: self.mostrar_cavidad(c, bool(estado)))
                g.addWidget(cb, i // 2, i % 2)
                self.checks[cav] = cb
            lay.addWidget(caja)
        caja_v = QGroupBox("Vistas")
        gv = QGridLayout(caja_v)
        for i, (tecla, vista) in enumerate(VISTAS_TECLA.items()):
            b = QPushButton(f"{tecla} {ETIQUETAS_VISTA[vista]}")
            b.clicked.connect(lambda _, v=vista: self.vista(v))
            gv.addWidget(b, i // 3, i % 3)
        lay.addWidget(caja_v)
        leyenda = QLabel(LEYENDA_INMERSIVA if self.inmersivo else LEYENDA_GENERAL)
        leyenda.setWordWrap(True)
        lay.addWidget(leyenda)
        self.estado = QLabel("")
        self.estado.setWordWrap(True)
        lay.addWidget(self.estado)
        div = QSplitter(Qt.Horizontal)
        div.addWidget(self.vtk_widget)
        div.addWidget(panel)
        div.setSizes([1000, 420])
        self.setCentralWidget(div)

    def _texto_inicial(self):
        if self.inmersivo:
            return ("<p>Haz clic en la cavidad para ver su ficha. Acércate con la rueda del ratón para "
                    "separarla en sus componentes y haz clic en cualquiera de ellos para ver toda su información.</p>")
        return "<p>Haz clic en una estructura para ver su ficha anatómica.</p>"

    # ------------------------------------------------------------------ despiece
    def _camara_cambiada(self, obj, evento):
        if not self._actualizando:
            self._tras_mover_camara()

    def _tras_mover_camara(self, seguir=None):
        """Tras mover la cámara: etiquetas y, si se cruza un umbral, transición entre ensamblada y separada.

        Girar o acercarse sin cruzar un umbral no mueve las piezas. `seguir` es la pieza que la cámara
        acompaña durante la transición (la que estaba bajo el cursor o la encuadrada).
        """
        if not self.explosion:
            return
        self._actualizando = True
        try:
            self.explosion.colocar_etiquetas()
            self.seleccionador.reiniciar()
            if self.explosion.automatico:
                objetivo = self.explosion.objetivo_segun_distancia()
                if objetivo != self.explosion.objetivo or (self.explosion.t != objetivo
                                                           and not self.temporizador.isActive()):
                    self._iniciar_transicion(objetivo, seguir)
        finally:
            self._actualizando = False

    def _iniciar_transicion(self, objetivo, seguir=None):
        self.explosion.objetivo = objetivo
        self._seguir = seguir if objetivo > 0.5 else None
        self._t_previo = time.time()
        if not self.temporizador.isActive():
            self.temporizador.start()

    def _paso_transicion(self):
        ahora = time.time()
        paso = (ahora - self._t_previo) / DURACION_TRANSICION
        self._t_previo = ahora
        t0, objetivo = self.explosion.t, self.explosion.objetivo
        t = min(t0 + paso, objetivo) if objetivo > t0 else max(t0 - paso, objetivo)
        self._actualizando = True
        try:
            if t < t0:  # al ensamblarse, la cavidad vuelve al centro de la vista
                self.explosion.recentrar((t0 - t) / t0)
            self.explosion.aplicar(t, seguir=self._seguir)
            self._refrescar_interfaz()
        finally:
            self._actualizando = False
        if t == objetivo:
            self.temporizador.stop()
            self._seguir = None
            self.seleccionador.reiniciar()
        self.ventana.Render()

    def _refrescar_interfaz(self):
        self.resaltador.seguir()
        if self.seleccion == self.explosion.envoltura and self.explosion.t > FIN_DESVANECIDO / 2:
            self.deseleccionar(renderizar=False)  # la envoltura se ha desvanecido
        self.indicador.setText(f"Separación: {100 * self.explosion.e:.0f} %")
        if self.explosion.automatico:
            self.deslizador.blockSignals(True)
            self.deslizador.setValue(int(round(100 * self.explosion.t)))
            self.deslizador.blockSignals(False)
        self.renderer.ResetCameraClippingRange()

    def _cambiar_automatico(self, estado):
        self.explosion.automatico = bool(estado)
        self.deslizador.setEnabled(not estado)
        if estado:
            self._tras_mover_camara()  # vuelve al estado que corresponde a la distancia
        else:
            self.temporizador.stop()
        self.ventana.Render()

    def _deslizador_movido(self, valor):
        if not self.explosion.automatico:
            self._actualizando = True
            try:
                self.explosion.aplicar(valor / 100.0)
                self.explosion.objetivo = 1.0 if self.explosion.t >= 0.5 else 0.0
                self._refrescar_interfaz()
            finally:
                self._actualizando = False
            self.seleccionador.reiniciar()
            self.ventana.Render()

    def _cambiar_etiquetas(self, estado):
        self.explosion.etiquetas_activas = bool(estado)
        self.explosion.colocar_etiquetas()
        self.ventana.Render()

    def rueda(self, x, y, sentido):
        """Acerca o aleja la cámara hacia el punto bajo el cursor.

        Si al acercarse la cavidad se separa, la cámara acompaña a la pieza que estaba bajo el cursor.
        """
        envoltura = [self.explosion.envoltura] if self.explosion and self.explosion.envoltura else []
        oid, punto = self.seleccionador.punto_en(x, y, envoltura)
        if punto is None:
            punto = punto_bajo_cursor(self.renderer, x, y)
        self._actualizando = True
        try:
            acercar_hacia(self.renderer.GetActiveCamera(), punto, FACTOR_RUEDA if sentido > 0 else 1.0 / FACTOR_RUEDA)
        finally:
            self._actualizando = False
        self._tras_mover_camara(seguir=oid)
        self.renderer.ResetCameraClippingRange()
        self.ventana.Render()

    def encuadrar_seleccion(self):
        """Acerca la cámara a la pieza seleccionada; en el modo inmersivo la cavidad se abre y la cámara la acompaña."""
        if not self.seleccion:
            return
        m = self.escena.meta[self.seleccion]
        centro = self.explosion.posicion_actual(self.seleccion) if self.explosion else np.array(m["centroide"])
        tam = float(np.max(np.array(m["caja"][1]) - np.array(m["caja"][0])))
        cam = self.renderer.GetActiveCamera()
        direccion = np.array(cam.GetPosition()) - np.array(cam.GetFocalPoint())
        direccion /= np.linalg.norm(direccion)
        abrir = bool(self.explosion and self.explosion.automatico and self.seleccion != self.explosion.envoltura)
        angulo = ANGULO_VISTA[1] if abrir else cam.GetViewAngle()
        distancia = max(0.6 * tam / np.tan(np.radians(angulo) / 2), 0.12)
        if abrir:  # por debajo del umbral de separación: la cavidad se abre (o sigue abierta)
            distancia = min(distancia, 0.95 * self.explosion.umbral_separar)
        self._actualizando = True
        try:
            cam.SetFocalPoint(*centro)
            cam.SetPosition(*(centro + direccion * distancia))
        finally:
            self._actualizando = False
        self._tras_mover_camara(seguir=self.seleccion)
        self.renderer.ResetCameraClippingRange()
        self.ventana.Render()

    # ------------------------------------------------------------------ acciones
    def clic(self, x, y):
        t0 = time.time()
        oid = self.seleccionador.clic(x, y)
        if oid is None:
            self.deseleccionar()
            return
        self.seleccionar(oid, origen="clic")
        self.estado.setText(f"Selección en {1000 * (time.time() - t0):.0f} ms")

    def seleccionar(self, oid, origen="clic"):
        actor = self.escena.actores[oid]
        self.resaltador.resaltar(actor)
        self.seleccion = oid
        meta = self.escena.meta[oid]
        ficha = self.repositorio.ficha_de_objeto(oid)
        prof = self.seleccionador.profundidad_actual() if origen == "clic" else (1, 1)
        relacionados = [self.escena.meta[o]["nombre"] for o in self.escena.meta
                        if o != oid and self.escena.meta[o]["ficha_id"] == meta["ficha_id"]]
        self.ficha.setHtml(html_ficha(ficha, meta, prof, relacionados))
        emitir("seleccion", objeto_id=oid, ficha_id=meta["ficha_id"], origen=origen, capa=prof[0],
               modelo=self.cavidad or "general")
        self.ventana.Render()

    def deseleccionar(self, renderizar=True):
        self.resaltador.quitar()
        self.seleccion = None
        self.seleccionador.reiniciar()
        self.ficha.setHtml(self._texto_inicial())
        if renderizar:
            self.ventana.Render()

    def buscar(self, texto):
        texto = texto.strip().lower()
        for oid, m in self.escena.meta.items():
            if m["seleccionable"] and texto and texto in m["nombre"].lower():
                if not self.escena.actores[oid].GetVisibility():
                    self.escena.actores[oid].SetVisibility(True)
                self.seleccionar(oid, origen="busqueda")
                return

    def mostrar_cavidad(self, cavidad, visible):
        self.cavidades_visibles[cavidad] = visible
        for oid, actor in self.escena.actores.items():
            m = self.escena.meta[oid]
            if m["cavidad"] == cavidad and not m["envoltura"]:
                actor.SetVisibility(visible and (m["visible_por_defecto"] or cavidad == "referencia"))
        if self.seleccion and self.escena.meta[self.seleccion]["cavidad"] == cavidad and not visible:
            self.deseleccionar()
        self.seleccionador.reiniciar()
        self.ventana.Render()

    def alternar_translucido(self):
        self.translucido = not self.translucido
        for oid, actor in self.escena.actores.items():
            if self.escena.meta[oid]["envoltura"]:
                continue
            base = self.escena.meta[oid]["opacidad"]
            nueva = min(base, OPACIDAD_TRANSLUCIDA) if self.translucido else base
            if oid == self.seleccion and self.resaltador._previo:
                c, a, _ = self.resaltador._previo
                self.resaltador._previo = (c, a, nueva)
            else:
                actor.GetProperty().SetOpacity(nueva)
        self.ventana.Render()

    def mostrar_todo(self):
        for cav, cb in self.checks.items():
            if cav != "referencia":
                cb.blockSignals(True)
                cb.setChecked(True)
                cb.blockSignals(False)
                self.cavidades_visibles[cav] = True
        for oid, actor in self.escena.actores.items():
            m = self.escena.meta[oid]
            if m["cavidad"] != "referencia" and not m["envoltura"]:
                actor.SetVisibility(True)
        if self.explosion:
            self.explosion.colocar_etiquetas()
            self._refrescar_interfaz()
        self.ventana.Render()

    def vista(self, nombre):
        manual = None
        if self.explosion:  # las vistas se calculan con la cavidad ensamblada
            self.temporizador.stop()
            self._seguir = None
            manual = None if self.explosion.automatico else self.explosion.t
            self._actualizando = True
            self.explosion.objetivo = 0.0
            self.explosion.aplicar(0.0)
        self.escena.aplicar_vista(nombre)
        if nombre == "craneal" and self.cavidad == "inguinal":  # oblicua desde craneoventral
            self.renderer.GetActiveCamera().Elevation(-25)
            self.renderer.GetActiveCamera().OrthogonalizeViewUp()
        if self.explosion:
            self.explosion.calibrar()
            if manual:
                self.explosion.aplicar(manual)
                self.explosion.objetivo = 1.0 if manual >= 0.5 else 0.0
            self._refrescar_interfaz()
            self._actualizando = False
        self.seleccionador.reiniciar()
        self.ventana.Render()

    def tecla(self, simbolo):
        s = simbolo or ""
        if s in CAVIDADES_TECLA and not self.inmersivo:
            cb = self.checks[CAVIDADES_TECLA[s]]
            cb.setChecked(not cb.isChecked())
        elif s in VISTAS_TECLA:
            self.vista(VISTAS_TECLA[s])
        elif s.lower() == "h" and self.seleccion:
            oid = self.seleccion
            self.deseleccionar()
            self.escena.actores[oid].SetVisibility(False)
            self.ventana.Render()
        elif s.lower() == "a":
            self.mostrar_todo()
        elif s.lower() == "t":
            self.alternar_translucido()
        elif s.lower() == "r":
            self.vista(VISTA_INICIAL[self.cavidad])
        elif s.lower() == "f":
            self.encuadrar_seleccion()
        elif s.lower() == "l" and self.explosion:
            self.etiq.setChecked(not self.etiq.isChecked())
        elif s == "Escape":
            self.deseleccionar()

    def closeEvent(self, evento):
        emitir("cierre", modelo=self.cavidad or "general", duracion_s=round(time.time() - self.inicio, 1))
        self.vtk_widget.Finalize()
        evento.accept()


def main(cavidad=None):
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # la app Java lee los eventos en UTF-8
    app = QApplication(sys.argv)
    visor = VisorCavidades(cavidad=cavidad)
    visor.show()
    visor.vtk_widget.Initialize()
    visor.vtk_widget.Start()
    sys.exit(app.exec_())


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
