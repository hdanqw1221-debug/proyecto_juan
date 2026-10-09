import sys
import os
import json
from PyQt5.QtWidgets import QApplication, QMainWindow, QFrame, QVBoxLayout, QLabel
from PyQt5.QtCore import Qt
import vtk
from vtkmodules.qt.QVTKRenderWindowInteractor import QVTKRenderWindowInteractor
from pathlib import Path



json_file = Path(__file__).parent / "sistema_muscular.json"
if os.path.exists(json_file):
    with open(json_file, "r", encoding="utf-8") as f:
        actor_info = json.load(f)
else:
    actor_info = {}

class InfoCard(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setStyleSheet("""
            background-color: white;
            border: 1px solid black;
            padding: 5px;
        """)
        self.label = QLabel("", self)
        self.label.setWordWrap(True)
        layout = QVBoxLayout(self)
        layout.addWidget(self.label)
        self.setLayout(layout)
        self.hide()  

    def update_info(self, info):
        self.label.setText(info)
        self.adjustSize()
        self.show()

def applyDarkening(actor, darken=True):
    """
    Para oscurecer la apariencia del actor se ajusta su property color,
    utilizando el valor original almacenado en actor.original_color.
    Al revertir, se restaura ese valor.
    """
    if darken:
        if not hasattr(actor, "original_color"):
            actor.original_color = actor.GetProperty().GetColor()
        orig = actor.original_color
        # Se multiplica cada componente por 0.8 para oscurecerlo
        actor.GetProperty().SetColor(orig[0]*0.8, orig[1]*0.8, orig[2]*0.8)
    else:
        if hasattr(actor, "original_color"):
            actor.GetProperty().SetColor(actor.original_color)

class MyInteractorStyle(vtk.vtkInteractorStyleTrackballCamera):
    def __init__(self, viewer):
        super().__init__()
        self.viewer = viewer
        self.AddObserver("LeftButtonReleaseEvent", self.onLeftButtonRelease)

    def onLeftButtonRelease(self, obj, event):
        clickPos = self.GetInteractor().GetEventPosition()
        picker = vtk.vtkPropPicker()
        picker.Pick(clickPos[0], clickPos[1], 0, self.viewer.renderer)
        actor = picker.GetActor()
        self.viewer.handleSelection(actor)
        self.OnLeftButtonUp()

class GLTFImporterViewer(QMainWindow):
    def __init__(self, glb_file):
        super().__init__()
        self.setWindowTitle("Visor GLTF - Sistema Muscular Equino")
        self.resize(1024, 768)

        self.frame = QFrame()
        self.layout = QVBoxLayout(self.frame)
        self.setCentralWidget(self.frame)

        self.vtkWidget = QVTKRenderWindowInteractor(self.frame)
        self.layout.addWidget(self.vtkWidget)

        self.renderWindow = self.vtkWidget.GetRenderWindow()
        self.renderer = vtk.vtkRenderer()
        self.renderer.SetBackground(0.2, 0.2, 0.2)
        self.renderWindow.AddRenderer(self.renderer)

        self.interactor = self.renderWindow.GetInteractor()
        self.style = MyInteractorStyle(self)
        self.interactor.SetInteractorStyle(self.style)

        # Variable para mantener la selección
        self.selected_actor = None

        self.infoCard = InfoCard(self)
        self.infoCard.move(self.width()-self.infoCard.width()-20, 20)

        if not os.path.exists(glb_file):
            sys.exit(1)
        # Usar vtkGLTFImporter para cargar la escena completa conservando texturas y materiales
        importer = vtk.vtkGLTFImporter()
        importer.SetFileName(glb_file)
        importer.SetRenderWindow(self.renderWindow)
        importer.Update()
        self.renderer.ResetCamera()

        # Asignar a cada actor importado un id único (por ejemplo, "objeto_1", "objeto_2", ...)
        actors = self.renderer.GetActors()
        actors.InitTraversal()
        counter = 1
        actor = actors.GetNextActor()
        while actor:
            actor.my_id = f"objeto_{counter}"
            # Guardar el color original
            actor.original_color = actor.GetProperty().GetColor()
            counter += 1
            actor = actors.GetNextActor()

        self.interactor.Initialize()

    def handleSelection(self, actor):
        if actor:
            if self.selected_actor == actor:
                # Si se hace clic en el mismo actor, se deselecciona: se restaura su color original
                applyDarkening(actor, darken=False)
                self.selected_actor = None
                self.infoCard.hide()
            else:
                if self.selected_actor:
                    applyDarkening(self.selected_actor, darken=False)
                self.selected_actor = actor
                applyDarkening(actor, darken=True)
                # Mostrar el nombre del objeto según actor_info o un mensaje por defecto
                actor_id = getattr(actor, "my_id", None)
                if actor_id and actor_id in actor_info:
                    info = actor_info[actor_id]
                    formatted_info = (
                        #f"Object: {info.get('object', '')}\n"
                        f"Nombre: {info.get('nombre', '')}\n"
                        f"Descripción: {info.get('descripcion', '')}"
                    )
                    self.infoCard.update_info(formatted_info)
                else:
                    self.infoCard.update_info("Target seleccionado")
                self.infoCard.move(self.width()-self.infoCard.width()-20, 20)
        else:
            if self.selected_actor:
                applyDarkening(self.selected_actor, darken=False)
            self.selected_actor = None
            self.infoCard.hide()
        self.renderWindow.Render()

    def closeEvent(self, event):
        self.vtkWidget.Finalize()
        event.accept()


if __name__ == '__main__':
    glb_file = Path(__file__).parent / "sistema_muscular.glb"
    app = QApplication(sys.argv)
    viewer = GLTFImporterViewer(glb_file)
    viewer.show()
    viewer.vtkWidget.Initialize()
    viewer.vtkWidget.Start()
    sys.exit(app.exec_())
