"""Pruebas del visor en modo offscreen: carga, selección simulada y fichas (§8)."""
import time
from pathlib import Path

import pytest

vtk = pytest.importorskip("vtk")

from visor.escena import DIR_MODELOS, Escena  # noqa: E402
from visor.repositorio_fichas import RepositorioFichas  # noqa: E402
from visor.seleccion import Seleccionador  # noqa: E402

pytestmark = pytest.mark.skipif(not (Path(DIR_MODELOS) / "manifiesto.json").exists(), reason="modelos no generados")


@pytest.fixture(scope="module")
def contexto():
    ren = vtk.vtkRenderer()
    ventana = vtk.vtkRenderWindow()
    ventana.SetOffScreenRendering(1)
    ventana.SetSize(1000, 700)
    ventana.AddRenderer(ren)
    t0 = time.time()
    escena = Escena(ren, DIR_MODELOS)
    carga = time.time() - t0
    escena.aplicar_vista("lateral_izquierda")
    ventana.Render()
    yield ren, ventana, escena, carga
    ventana.Finalize()


def _a_pantalla(ren, punto):
    ren.SetWorldPoint(*punto, 1.0)
    ren.WorldToDisplay()
    x, y, _ = ren.GetDisplayPoint()
    return int(round(x)), int(round(y))


def test_carga_en_menos_de_5_s(contexto):
    _, _, escena, carga = contexto
    assert len(escena.actores) == len(escena.meta) > 50
    assert carga < 5.0


def test_referencias_no_seleccionables(contexto):
    _, _, escena, _ = contexto
    for oid, actor in escena.actores.items():
        assert bool(actor.GetPickable()) == escena.meta[oid]["seleccionable"]


@pytest.mark.parametrize("objeto_id", ["TOR_CORAZON", "ABD_RINON_DER", "ABD_RINON_IZQ", "PEL_VEJIGA",
                                       "ING_TESTICULO_IZQ", "TOR_PULMON_IZQ"])
def test_seleccion_en_el_centro_de_un_organo(contexto, objeto_id):
    ren, ventana, escena, _ = contexto
    sel = Seleccionador(ren, escena)
    x, y = _a_pantalla(ren, escena.meta[objeto_id]["centroide"])
    pila = sel.estructuras_en(x, y)
    assert objeto_id in pila, (objeto_id, pila)
    # Profundizar: clics repetidos en el mismo punto recorren la pila; cada clic tarda < 200 ms.
    elegido = []
    for _ in range(len(pila)):
        t0 = time.time()
        elegido.append(sel.clic(x, y))
        assert time.time() - t0 < 0.2
    assert elegido == pila
    assert sel.clic(x, y) == pila[0]  # tras la capa más profunda vuelve a la primera


def test_clic_en_el_fondo_no_selecciona(contexto):
    ren, _, escena, _ = contexto
    assert Seleccionador(ren, escena).clic(2, 2) is None


def test_repositorio_json_cubre_todos_los_objetos_seleccionables(contexto):
    _, _, escena, _ = contexto
    repo = RepositorioFichas(usar_bd=False)
    assert repo.origen == "json"
    for oid, m in escena.meta.items():
        if m["seleccionable"]:
            assert repo.ficha_de_objeto(oid)["ficha_id"] == m["ficha_id"]


def test_repositorio_bd_coincide_con_json():
    repo_bd = RepositorioFichas()
    if repo_bd.origen != "postgresql":
        pytest.skip(f"PostgreSQL no disponible: {repo_bd.error_bd}")
    repo_json = RepositorioFichas(usar_bd=False)
    assert set(repo_bd.fichas) == set(repo_json.fichas)
    for fid, f in repo_json.fichas.items():
        g = repo_bd.fichas[fid]
        for campo in ("nombre", "nombre_latino", "descripcion", "consideraciones_clinicas", "pendiente_validacion"):
            assert g[campo] == f[campo], (fid, campo)
        assert sorted(g["objetos"]) == sorted(f["objetos"])
