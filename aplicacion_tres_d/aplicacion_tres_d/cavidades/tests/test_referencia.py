"""Pruebas del sistema de referencia corporal (§2 y §8)."""
import numpy as np
import pytest

from anatomia import referencia as ref


def test_costillas_y_eic():
    assert ref.x_costilla(1) == pytest.approx(1.60)
    assert ref.x_costilla(18) == pytest.approx(0.75)
    assert ref.x_eic(6) == pytest.approx(1.325)
    assert ref.eic_de_x(ref.x_eic(11)) == pytest.approx(11.0)


def test_vertice_diafragma_en_eic6():
    x, y, z = ref.vertice_diafragma()
    assert x == pytest.approx(1.316, abs=0.002)
    assert abs(x - ref.x_eic(6)) <= 0.02


def test_hiato_esofagico_en_eic_13_14():
    x = float(ref.x_diafragma(0.0, 0.58))
    assert x == pytest.approx(0.90, abs=0.01)
    # Entre los EIC 13 y 14 con la tolerancia de 0,03 m de la proyección.
    assert ref.x_eic(14) - 0.03 <= x <= ref.x_eic(13) + 0.03
    xh, _, _ = ref.punto_abertura("hiato_esofagico")
    assert ref.x_eic(14) - 0.03 <= xh <= ref.x_eic(13) + 0.03


def test_hiato_aortico_dorsal_y_caudal():
    x, _, z = ref.punto_abertura("hiato_aortico")
    assert x == pytest.approx(0.79, abs=0.01)
    assert z > 0.6


def test_foramen_vena_cava_a_la_derecha_y_craneal_al_hiato_esofagico():
    xf, yf, _ = ref.punto_abertura("foramen_vena_cava")
    xh, _, _ = ref.punto_abertura("hiato_esofagico")
    assert yf < 0 and xf > xh


def test_envolvente_continua():
    xs = np.linspace(ref.X_MIN_ENVOLVENTE, ref.X_MAX_ENVOLVENTE, 2000)
    w, zv, zd = ref.envolvente(xs)
    paso = xs[1] - xs[0]
    for serie in (w, zv, zd):
        assert np.max(np.abs(np.diff(serie))) < 2.0 * paso  # pendiente acotada: sin saltos
    assert np.all(w > 0) and np.all(zd > zv)


def test_clasificacion_de_cavidades():
    assert ref.cavidad_de_punto(1.40, 0.0, 0.35) == "toracica"
    assert ref.cavidad_de_punto(0.60, 0.0, 0.35) == "abdominal"
    assert ref.cavidad_de_punto(0.10, 0.0, 0.40) == "pelvica"
    assert ref.cavidad_de_punto(0.60, 0.50, 0.35) == "exterior"
    # Abdomen intratorácico: craneal al arco costal pero caudal al diafragma.
    assert ref.cavidad_de_punto(1.15, 0.0, 0.05) == "abdominal"


def test_borde_basal_deja_receso_costodiafragmatico():
    # Cerca de la pared costal el borde basal queda craneal a la inserción del diafragma.
    z = 0.30
    y_pared = 0.25
    assert ref.x_borde_basal_pulmon(z) > ref.x_diafragma(y_pared, z)


def test_recorte_a_cavidad():
    p = np.array([[1.30, 0.0, 0.30], [0.60, 0.40, 0.35]])
    q = ref.recortar_a_cavidad(p, "abdominal", margen=0.01)
    assert q[0, 0] <= ref.x_diafragma(q[0, 1], q[0, 2]) - 0.01 + 1e-9
    assert ref.dentro_envolvente(q[1, 0], q[1, 1], q[1, 2])
