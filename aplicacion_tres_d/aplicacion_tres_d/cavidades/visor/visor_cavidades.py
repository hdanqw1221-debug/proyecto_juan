"""Visor interactivo de las cavidades torácica, abdominal e inguinal del caballo (PyQt5 + VTK).

- Clic (sin arrastre) sobre una estructura: la selecciona y muestra su ficha.
- Clic repetido en el mismo punto: pasa a la estructura siguiente a lo largo del rayo.
- Eventos en la salida estándar como líneas JSON (inicio, selección, cierre) para la app Java.
"""
import html
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import vtk  # noqa: E402
from PyQt5.QtCore import Qt, QStringListModel  # noqa: E402
from PyQt5.QtWidgets import (QApplication, QCheckBox, QCompleter, QGridLayout, QGroupBox, QLabel,  # noqa: E402
                             QLineEdit, QMainWindow, QPushButton, QSplitter, QTextBrowser, QVBoxLayout, QWidget)
from vtkmodules.qt.QVTKRenderWindowInteractor import QVTKRenderWindowInteractor  # noqa: E402

from visor.escena import DIR_MODELOS, Escena  # noqa: E402
from visor.repositorio_fichas import RepositorioFichas  # noqa: E402
from visor.seleccion import UMBRAL_ARRASTRE_PX, Resaltador, Seleccionador  # noqa: E402

CAVIDADES_TECLA = {"1": "toracica", "2": "abdominal", "3": "inguinal", "4": "pelvica", "5": "referencia"}
NOMBRES_CAVIDAD = {"toracica": "Torácica", "abdominal": "Abdominal", "inguinal": "Región inguinal",
                   "pelvica": "Pélvica (ref.)", "referencia": "Referencias"}
VISTAS_TECLA = {"F1": "lateral_izquierda", "F2": "lateral_derecha", "F3": "ventral", "F4": "dorsal", "F5": "craneal"}
ETIQUETAS_VISTA = {"lateral_izquierda": "Lateral izq.", "lateral_derecha": "Lateral der.", "ventral": "Ventral",
                   "dorsal": "Dorsal", "craneal": "Craneal"}
CAMPOS_FICHA = [
    ("descripcion", "Descripción"), ("topografia", "Topografía"), ("proyeccion_superficial", "Proyección superficial"),
    ("relaciones", "Relaciones"), ("medios_de_fijacion", "Medios de fijación"),
    ("irrigacion_drenaje", "Irrigación y drenaje"), ("dimensiones", "Dimensiones"),
    ("consideraciones_clinicas", "Consideraciones clínicas"), ("exploracion", "Exploración"),
]
OPACIDAD_TRANSLUCIDA = 0.15
LEYENDA = ("<b>Atajos</b><br>Clic: seleccionar · clic repetido: estructura más profunda<br>"
           "1-5: Torácica / Abdominal / Inguinal / Pélvica / Referencias<br>"
           "H: ocultar selección · A: mostrar todo · T: resto translúcido<br>"
           "R: restablecer cámara · Esc: deseleccionar · F1-F5: vistas")


def emitir(evento, **datos):
    """Evento para la aplicación Java: una línea JSON por la salida estándar."""
    print(json.dumps({"evento": evento, "t": round(time.time(), 3), **datos}, ensure_ascii=False), flush=True)


def html_ficha(ficha, meta, profundidad):
    if ficha is None:
        return f"<h3>{html.escape(meta['nombre'])}</h3><p><i>Sin ficha anatómica.</i></p>"
    pend = set(ficha.get("campos_pendientes") or [])
    partes = [f"<h2 style='margin-bottom:0'>{html.escape(meta['nombre'])}</h2>",
              f"<p style='margin-top:2px'><i>{html.escape(ficha['nombre_latino'])}</i><br>"
              f"<small>{html.escape(ficha['nombre'])} · {html.escape(ficha['cavidad'])} · "
              f"{html.escape(ficha['sistema'])} · objeto {html.escape(meta['objeto_id'])}</small></p>"]
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
    return "\n".join(partes)


class EstiloInteraccion(vtk.vtkInteractorStyleTrackballCamera):
    """Trackball con distinción clic/arrastre; las teclas las gestiona el visor."""

    def __init__(self, visor):
        super().__init__()
        self.visor = visor
        self._inicio = None
        self.AddObserver("LeftButtonPressEvent", self._presionar)
        self.AddObserver("LeftButtonReleaseEvent", self._soltar)
        self.AddObserver("CharEvent", lambda o, e: None)  # anula los atajos por defecto de VTK (q, e, w, 3…)
        self.AddObserver("KeyPressEvent", self._tecla)

    def _presionar(self, obj, evento):
        self._inicio = self.GetInteractor().GetEventPosition()
        self.OnLeftButtonDown()

    def _soltar(self, obj, evento):
        fin = self.GetInteractor().GetEventPosition()
        self.OnLeftButtonUp()
        if self._inicio and max(abs(fin[0] - self._inicio[0]), abs(fin[1] - self._inicio[1])) < UMBRAL_ARRASTRE_PX:
            self.visor.clic(*fin)
        self._inicio = None

    def _tecla(self, obj, evento):
        self.visor.tecla(self.GetInteractor().GetKeySym())


class VisorCavidades(QMainWindow):
    def __init__(self, dir_modelos=DIR_MODELOS):
        super().__init__()
        t0 = time.time()
        self.setWindowTitle("Simulador 3D - Cavidades torácica, abdominal e inguinal del equino")
        self.resize(1400, 860)
        self.vtk_widget = QVTKRenderWindowInteractor(self)
        self.ventana = self.vtk_widget.GetRenderWindow()
        self.ventana.SetAlphaBitPlanes(1)
        self.ventana.SetMultiSamples(0)
        self.renderer = vtk.vtkRenderer()
        self.renderer.SetBackground(0.16, 0.17, 0.19)
        self.renderer.SetBackground2(0.30, 0.32, 0.36)
        self.renderer.GradientBackgroundOn()
        self.renderer.SetUseDepthPeeling(1)
        self.renderer.SetMaximumNumberOfPeels(8)
        self.ventana.AddRenderer(self.renderer)
        self.escena = Escena(self.renderer, dir_modelos, decimar_si_supera=None)
        self.repositorio = RepositorioFichas()
        self.seleccionador = Seleccionador(self.renderer, self.escena)
        self.resaltador = Resaltador(self.renderer)
        self.seleccion = None
        self.translucido = False
        self.cavidades_visibles = {c: True for c in CAVIDADES_TECLA.values()}
        self.cavidades_visibles["referencia"] = False
        self._construir_panel()
        self.interactor = self.ventana.GetInteractor()
        self.interactor.SetInteractorStyle(EstiloInteraccion(self))
        self.escena.aplicar_vista("lateral_izquierda")
        self.tiempo_carga = time.time() - t0
        self.inicio = time.time()
        self.estado.setText(f"{len(self.escena.actores)} estructuras · fichas desde {self.repositorio.origen} · "
                            f"carga {self.tiempo_carga:.1f} s")
        emitir("inicio", objetos=len(self.escena.actores), fuente_fichas=self.repositorio.origen,
               carga_s=round(self.tiempo_carga, 2))

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
        self.ficha.setHtml("<p>Haz clic en una estructura para ver su ficha anatómica.</p>")
        lay.addWidget(self.ficha, 1)
        caja = QGroupBox("Cavidades")
        g = QGridLayout(caja)
        self.checks = {}
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
        leyenda = QLabel(LEYENDA)
        leyenda.setWordWrap(True)
        lay.addWidget(leyenda)
        self.estado = QLabel("")
        self.estado.setWordWrap(True)
        lay.addWidget(self.estado)
        div = QSplitter(Qt.Horizontal)
        div.addWidget(self.vtk_widget)
        div.addWidget(panel)
        div.setSizes([1000, 400])
        self.setCentralWidget(div)

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
        self.ficha.setHtml(html_ficha(ficha, meta, prof))
        emitir("seleccion", objeto_id=oid, ficha_id=meta["ficha_id"], origen=origen, capa=prof[0])
        self.ventana.Render()

    def deseleccionar(self):
        self.resaltador.quitar()
        self.seleccion = None
        self.seleccionador.reiniciar()
        self.ficha.setHtml("<p>Haz clic en una estructura para ver su ficha anatómica.</p>")
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
            if m["cavidad"] == cavidad:
                actor.SetVisibility(visible and (m["visible_por_defecto"] or cavidad == "referencia"))
        if self.seleccion and self.escena.meta[self.seleccion]["cavidad"] == cavidad and not visible:
            self.deseleccionar()
        self.seleccionador.reiniciar()
        self.ventana.Render()

    def alternar_translucido(self):
        self.translucido = not self.translucido
        for oid, actor in self.escena.actores.items():
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
            if m["cavidad"] != "referencia":
                actor.SetVisibility(True)
        self.ventana.Render()

    def vista(self, nombre):
        self.escena.aplicar_vista(nombre)
        self.seleccionador.reiniciar()
        self.ventana.Render()

    def tecla(self, simbolo):
        s = simbolo or ""
        if s in CAVIDADES_TECLA:
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
            self.vista("lateral_izquierda")
        elif s == "Escape":
            self.deseleccionar()

    def closeEvent(self, evento):
        emitir("cierre", duracion_s=round(time.time() - self.inicio, 1))
        self.vtk_widget.Finalize()
        evento.accept()


def main():
    app = QApplication(sys.argv)
    visor = VisorCavidades()
    visor.show()
    visor.vtk_widget.Initialize()
    visor.vtk_widget.Start()
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
