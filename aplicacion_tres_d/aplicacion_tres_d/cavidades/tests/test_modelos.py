"""Pruebas de los modelos generados (§8). Requieren haber ejecutado blender/generar_cavidades.py."""
import json
from pathlib import Path

import numpy as np
import pytest

from anatomia import catalogo as cat
from anatomia import referencia as ref
from blender.exportar import leer_obj

MODELOS = Path(__file__).resolve().parents[1] / "modelos"
pytestmark = pytest.mark.skipif(not (MODELOS / "manifiesto.json").exists(), reason="modelos no generados")


@pytest.fixture(scope="module")
def manifiesto():
    return json.loads((MODELOS / "manifiesto.json").read_text(encoding="utf-8"))


@pytest.fixture(scope="module")
def por_id(manifiesto):
    return {o["objeto_id"]: o for o in manifiesto["objetos"]}


def test_todos_los_objetos_del_catalogo_existen(por_id):
    assert set(por_id) == set(cat.POR_ID)
    for o in por_id.values():
        assert (MODELOS / o["archivo"]).exists(), o["archivo"]


def test_mallas_cerradas_manifold(por_id):
    for oid, o in por_id.items():
        V, T = leer_obj(MODELOS / o["archivo"])
        aristas = np.sort(np.vstack([T[:, [0, 1]], T[:, [1, 2]], T[:, [2, 0]]]), axis=1)
        _, cuenta = np.unique(aristas, axis=0, return_counts=True)
        assert np.all(cuenta == 2), f"{oid}: aristas abiertas o no manifold"
        assert len(np.unique(T)) == len(V), f"{oid}: vértices sueltos"
        assert o["manifold"], oid


def test_presupuesto_de_triangulos(por_id):
    for oid, o in por_id.items():
        if o["cavidad"] == "referencia":
            continue
        tope = 45000 if o.get("envoltura") else 15000  # las envolturas llevan relieve costal
        assert 2000 <= o["n_triangulos"] <= tope, (oid, o["n_triangulos"])


def test_centroide_en_la_cavidad_correcta(por_id):
    for oid, o in por_id.items():
        x, y, z = o["centroide"]
        cav = o["cavidad"]
        if oid == "TOR_DIAFRAGMA" or cav == "referencia":
            continue
        if cav in ("toracica", "abdominal"):
            assert ref.cavidad_de_punto(x, y, z) == cav, (oid, o["centroide"])
        elif cav == "pelvica":
            assert x < ref.X_ABERTURA_PELVICA + 0.02, oid
        elif cav == "inguinal":  # pared ventral caudal y escroto
            assert x < 0.35 and z < 0.30, (oid, o["centroide"])


def test_asimetrias_izquierda_derecha(por_id):
    c = {k: v["centroide"] for k, v in por_id.items()}
    assert c["TOR_CORAZON"][1] > 0 and c["ABD_ESTOMAGO"][1] > 0
    assert c["ABD_HIGADO"][1] < 0 and c["ABD_CIEGO"][1] < 0 and c["ABD_RINON_DER"][1] < 0
    assert c["ABD_RINON_DER"][0] > c["ABD_RINON_IZQ"][0]  # riñón derecho más craneal


def test_corazon_entre_costillas_3_y_6(por_id):
    x = por_id["TOR_CORAZON"]["centroide"][0]
    assert ref.x_costilla(6) <= x <= ref.x_costilla(3)


def test_abdomen_intratoracico(por_id):
    """Hígado, estómago y flexuras craneales quedan craneales a la última costilla (dentro del arco costal)."""
    for oid in ("ABD_HIGADO", "ABD_ESTOMAGO", "ABD_FLEXURA_ESTERNAL", "ABD_FLEXURA_DIAFRAGMATICA"):
        assert por_id[oid]["centroide"][0] > ref.x_costilla(18), oid


def test_cupula_diafragmatica(manifiesto, por_id):
    xv = manifiesto["vertice_diafragma"][0]
    assert abs(xv - ref.x_eic(6)) <= 0.02
    caja = por_id["TOR_DIAFRAGMA"]["caja"]
    assert caja[1][0] - caja[0][0] > 0.4  # cúpula profunda: más de 40 cm craneocaudales


def test_flexura_pelvica_mas_estrecha_que_colon_ventral(por_id):
    def calibre(oid):
        V, _ = leer_obj(MODELOS / por_id[oid]["archivo"])
        return np.ptp(V, axis=0).min()
    assert calibre("ABD_FLEXURA_PELVICA") < calibre("ABD_COLON_VENTRAL_IZQ")


def test_envolturas_contienen_su_cavidad(por_id):
    """La envoltura de cada cavidad engloba los centroides de sus estructuras (vista desde lejos)."""
    for cav in ("toracica", "abdominal"):
        env = next(o for o in por_id.values() if o["cavidad"] == cav and o.get("envoltura"))
        (x0, y0, z0), (x1, y1, z1) = env["caja"]
        for o in por_id.values():
            if o["cavidad"] == cav and not o.get("envoltura") and o["objeto_id"] != "TOR_DIAFRAGMA":
                x, y, z = o["centroide"]
                assert x0 - 0.02 <= x <= x1 + 0.02 and y0 - 0.02 <= y <= y1 + 0.02 and z0 - 0.02 <= z <= z1 + 0.02,                     (env["objeto_id"], o["objeto_id"])


def test_glb_conserva_los_identificadores():
    vtk = pytest.importorskip("vtk")
    imp = vtk.vtkGLTFImporter()
    imp.SetFileName(str(MODELOS / "escena.glb"))
    ventana = vtk.vtkRenderWindow()
    ventana.SetOffScreenRendering(1)
    imp.SetRenderWindow(ventana)
    imp.Update()
    jer = imp.GetSceneHierarchy()
    nombres = set()
    for nodo in jer.GetChildNodes(jer.GetRootNode(), True):
        nombres.add(jer.GetAttributeOrDefault(nodo, "name", ""))
        nombres.add(jer.GetAttributeOrDefault(nodo, "parent_node_name", ""))
    assert set(cat.POR_ID) <= nombres
