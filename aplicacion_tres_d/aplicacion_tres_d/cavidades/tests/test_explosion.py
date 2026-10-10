"""Pruebas del modelo inmersivo: la cavidad se ve completa de lejos y se despieza al acercarse."""
from pathlib import Path

import numpy as np
import pytest

vtk = pytest.importorskip("vtk")

from visor.escena import DIR_MODELOS, Escena, configurar_render_realista  # noqa: E402
from visor.explosion import ControladorExplosion, texto_etiqueta  # noqa: E402
from visor.seleccion import Seleccionador  # noqa: E402

pytestmark = pytest.mark.skipif(not (Path(DIR_MODELOS) / "manifiesto.json").exists(), reason="modelos no generados")


def _escena(cavidades, cavidad):
    ren = vtk.vtkRenderer()
    ventana = vtk.vtkRenderWindow()
    ventana.SetOffScreenRendering(1)
    ventana.SetSize(900, 650)
    ventana.AddRenderer(ren)
    escena = Escena(ren, DIR_MODELOS, cavidades=cavidades, realista=True, mostrar_envolturas=True)
    configurar_render_realista(ren, ventana)
    ctl = ControladorExplosion(ren, escena, cavidad)
    ctl.aplicar(0.0)
    escena.aplicar_vista("lateral_izquierda")
    ctl.calibrar()
    ventana.Render()
    return ren, ventana, escena, ctl


def _acercar(ren, ctl, factor):
    cam = ren.GetActiveCamera()
    f, p = np.array(cam.GetFocalPoint()), np.array(cam.GetPosition())
    cam.SetPosition(*(f + (p - f) / np.linalg.norm(p - f) * ctl.d_lejos * factor))
    ctl.actualizar()
    ren.ResetCameraClippingRange()


def _a_pantalla(ren, punto):
    ren.SetWorldPoint(*punto, 1.0)
    ren.WorldToDisplay()
    x, y, _ = ren.GetDisplayPoint()
    return int(round(x)), int(round(y))


@pytest.fixture(scope="module")
def toracica():
    ren, ventana, escena, ctl = _escena(["toracica"], "toracica")
    yield ren, ventana, escena, ctl
    ventana.Finalize()


def test_solo_carga_su_cavidad(toracica):
    _, _, escena, ctl = toracica
    assert {m["cavidad"] for m in escena.meta.values()} == {"toracica"}
    assert ctl.envoltura == "TOR_CAVIDAD_TORACICA"


def test_de_lejos_se_ve_y_se_selecciona_la_cavidad(toracica):
    ren, ventana, escena, ctl = toracica
    _acercar(ren, ctl, 1.0)
    ventana.Render()
    env = escena.actores[ctl.envoltura]
    assert ctl.t == 0.0 and env.GetVisibility() and env.GetProperty().GetOpacity() == pytest.approx(1.0)
    assert all(np.allclose(escena.actores[o].GetPosition(), 0) for o in ctl.desplazamiento)
    x, y = _a_pantalla(ren, escena.meta["TOR_CORAZON"]["centroide"])
    assert Seleccionador(ren, escena).clic(x, y) == ctl.envoltura


def test_de_cerca_se_despieza(toracica):
    ren, ventana, escena, ctl = toracica
    _acercar(ren, ctl, 0.55)
    ventana.Render()
    assert ctl.t == 1.0 and ctl.e == pytest.approx(1.0)
    assert not escena.actores[ctl.envoltura].GetVisibility()
    for pulmon in ("TOR_PULMON_IZQ", "TOR_PULMON_DER"):
        assert np.linalg.norm(escena.actores[pulmon].GetPosition()) > 0.2
    assert any(et.GetVisibility() for et in ctl.etiquetas.values())
    # La pieza separada se puede seleccionar en su nueva posición.
    x, y = _a_pantalla(ren, ctl.posicion_actual("TOR_CORAZON"))
    assert "TOR_CORAZON" in Seleccionador(ren, escena).estructuras_en(x, y)


def test_al_alejarse_se_vuelve_a_ensamblar(toracica):
    ren, ventana, escena, ctl = toracica
    _acercar(ren, ctl, 0.55)
    _acercar(ren, ctl, 1.0)
    assert ctl.e == 0.0 and escena.actores[ctl.envoltura].GetVisibility()


def test_despiece_no_va_hacia_la_camara(toracica):
    """En vista lateral el pulmón izquierdo no se acerca al observador: se aparta dentro de la pantalla."""
    ren, _, escena, ctl = toracica
    _acercar(ren, ctl, 0.55)
    cam = ren.GetActiveCamera()
    vista = np.array(cam.GetPosition()) - np.array(cam.GetFocalPoint())
    vista /= np.linalg.norm(vista)
    v = np.array(escena.actores["TOR_PULMON_IZQ"].GetPosition())
    assert abs(np.dot(v, vista)) < 0.5 * np.linalg.norm(v)


@pytest.mark.parametrize("cavidades,cavidad,envoltura", [(["abdominal", "pelvica"], "abdominal", "ABD_CAVIDAD_ABDOMINAL"),
                                                         (["inguinal"], "inguinal", "ING_REGION_INGUINAL")])
def test_otras_cavidades(cavidades, cavidad, envoltura):
    ren, ventana, escena, ctl = _escena(cavidades, cavidad)
    assert ctl.envoltura == envoltura
    _acercar(ren, ctl, 0.55)
    ventana.Render()
    assert not escena.actores[envoltura].GetVisibility()
    assert max(np.linalg.norm(escena.actores[o].GetPosition()) for o in ctl.desplazamiento) > 0.1
    ventana.Finalize()


def test_texto_de_etiquetas():
    assert texto_etiqueta("Pulmón izquierdo") == "Pulmón (izq.)"
    assert texto_etiqueta("Anillo inguinal profundo / vaginal derecho") == "Anillo inguinal profundo (der.)"
    assert texto_etiqueta("Colon descendente (colon menor)") == "Colon descendente"
