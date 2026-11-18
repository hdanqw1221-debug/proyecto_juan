"""C08: registro de fallos (faulthandler) en los cuatro puntos de entrada y protección de la rueda del ratón."""
import subprocess
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

RAIZ_APP = Path(__file__).resolve().parents[2]
PUNTOS_DE_ENTRADA = ["cavidades_equinas.py", "cavidad_toracica.py", "cavidad_abdominal.py", "region_inguinal.py"]


@pytest.mark.parametrize("script", PUNTOS_DE_ENTRADA)
def test_los_cuatro_puntos_de_entrada_activan_faulthandler(script):
    codigo = (RAIZ_APP / script).read_text(encoding="utf-8")
    assert "activar_faulthandler(" in codigo
    assert codigo.index("activar_faulthandler(") < codigo.index("from visor.visor_cavidades import main"), \
        "debe activarse antes de importar VTK y Qt, que son los que pueden matar el proceso"


def test_un_cierre_forzado_deja_la_pila_de_python_en_el_registro(tmp_path):
    programa = (
        "import sys, faulthandler\n"
        f"sys.path.insert(0, r'{RAIZ_APP / 'cavidades'}')\n"
        "from visor.diagnostico import activar_faulthandler\n"
        "ruta = activar_faulthandler('prueba')\n"
        "print(ruta, flush=True)\n"
        "def disparar():\n"
        "    faulthandler._sigsegv()  # simula una violación de acceso en código nativo\n"
        "disparar()\n"
    )
    r = subprocess.run([sys.executable, "-c", programa], capture_output=True, text=True,
                       env={**__import__("os").environ, "CAVIDADES_LOG_DIR": str(tmp_path)}, timeout=60)
    assert r.returncode != 0, "el proceso debía morir"
    registro = tmp_path / "visor_prueba.log"
    assert registro.exists()
    texto = registro.read_text(encoding="utf-8")
    assert "inicio" in texto and "pid=" in texto
    assert "Windows fatal exception" in texto or "Fatal Python error" in texto, texto
    assert "disparar" in texto, "la pila de Python indica dónde cayó"


def test_activar_faulthandler_no_rompe_el_arranque_si_no_puede_escribir(tmp_path, monkeypatch):
    sys.path.insert(0, str(RAIZ_APP / "cavidades"))
    from visor import diagnostico
    bloqueo = tmp_path / "archivo"
    bloqueo.write_text("no es una carpeta")
    monkeypatch.setenv("CAVIDADES_LOG_DIR", str(bloqueo / "dentro"))
    assert diagnostico.activar_faulthandler("x") is None


def _visor_falso(transicion_activa, en_rueda=False):
    llamadas = []

    class Selec:
        def punto_en(self, *a):
            llamadas.append("punto_en")
            raise RuntimeError("fallo simulado del selector")

    return SimpleNamespace(
        _en_rueda=en_rueda, explosion=SimpleNamespace(envoltura=None),
        temporizador=SimpleNamespace(isActive=lambda: transicion_activa),
        seleccionador=Selec(), llamadas=llamadas)


def test_la_rueda_se_ignora_durante_la_transicion_del_despiece():
    pytest.importorskip("vtk")
    from visor.visor_cavidades import VisorCavidades
    visor = _visor_falso(transicion_activa=True)
    VisorCavidades.rueda(visor, 10, 10, 1)
    assert visor.llamadas == [], "no debe tocar el selector mientras las piezas se mueven"


def test_una_excepcion_en_la_rueda_no_cierra_el_visor_y_libera_el_bloqueo(capsys):
    pytest.importorskip("vtk")
    from visor.visor_cavidades import VisorCavidades
    visor = _visor_falso(transicion_activa=False)
    VisorCavidades.rueda(visor, 10, 10, 1)  # no debe propagar la excepción
    assert visor.llamadas == ["punto_en"]
    assert visor._en_rueda is False
    assert "fallo simulado del selector" in capsys.readouterr().err
    VisorCavidades.rueda(visor, 10, 10, -1)  # y la siguiente rueda se procesa
    assert visor.llamadas == ["punto_en", "punto_en"]


def test_una_rueda_reentrante_se_ignora():
    pytest.importorskip("vtk")
    from visor.visor_cavidades import VisorCavidades
    visor = _visor_falso(transicion_activa=False, en_rueda=True)
    VisorCavidades.rueda(visor, 10, 10, 1)
    assert visor.llamadas == []
