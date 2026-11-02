"""Topología de las mallas generadas: cada estructura es una sola pieza y sin túneles, salvo los
agujeros anatómicos (aberturas del diafragma, anillos inguinales). Detecta asas fusionadas, puentes
entre tubos, tubos tallados como túneles a través de un órgano y fragmentos sueltos."""
import json
from pathlib import Path

import numpy as np
import pytest

from blender.exportar import leer_obj

MODELOS = Path(__file__).resolve().parents[1] / "modelos"
pytestmark = pytest.mark.skipif(not (MODELOS / "manifiesto.json").exists(), reason="modelos no generados")

# Agujeros reales: hiatos aórtico y esofágico y foramen de la vena cava; anillo pancreático (paso de la
# vena porta); anillos inguinales (toroides); en la envoltura inguinal, el puente de pared entre los dos
# cuellos del escroto.
GENERO_ANATOMICO = {
    "TOR_DIAFRAGMA": 3,
    "ABD_PANCREAS": 1,
    "ING_ANILLO_PROFUNDO_IZQ": 1, "ING_ANILLO_PROFUNDO_DER": 1,
    "ING_ANILLO_SUPERFICIAL_IZQ": 1, "ING_ANILLO_SUPERFICIAL_DER": 1,
    "ING_REGION_INGUINAL": 1,
}


def topologia(V, T):
    """(número de piezas conexas, género total) de una malla triangular cerrada."""
    padre = np.arange(len(V))

    def raiz(i):
        while padre[i] != i:
            padre[i] = padre[padre[i]]
            i = padre[i]
        return i

    for a, b, c in T:
        ra = raiz(a)
        padre[raiz(b)] = ra
        padre[raiz(c)] = ra
    piezas = len({raiz(i) for i in range(len(V))})
    aristas = np.unique(np.sort(np.vstack([T[:, [0, 1]], T[:, [1, 2]], T[:, [2, 0]]]), axis=1), axis=0)
    euler = len(V) - len(aristas) + len(T)
    return piezas, (2 * piezas - euler) // 2


@pytest.fixture(scope="module")
def topologias():
    manifiesto = json.loads((MODELOS / "manifiesto.json").read_text(encoding="utf-8"))
    salida = {}
    for o in manifiesto["objetos"]:
        if o["cavidad"] == "referencia":  # costillas y líneas topográficas: muchas piezas a propósito
            continue
        salida[o["objeto_id"]] = topologia(*leer_obj(MODELOS / o["archivo"]))
    return salida


def test_una_sola_pieza(topologias):
    sueltas = {oid: n for oid, (n, _) in topologias.items() if n != 1}
    assert not sueltas, f"estructuras partidas en varias piezas: {sueltas}"


def test_sin_tuneles_ni_asas_fusionadas(topologias):
    distintos = {oid: g for oid, (_, g) in topologias.items() if g != GENERO_ANATOMICO.get(oid, 0)}
    assert not distintos, f"género inesperado (túneles o asas fusionadas): {distintos}"
