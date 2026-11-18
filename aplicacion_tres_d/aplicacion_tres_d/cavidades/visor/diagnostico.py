"""Diagnóstico de cierres del visor (C08): `faulthandler` escribe en un archivo de registro.

Un cierre por violación de acceso (VTK/OpenGL/Qt) no deja traza de Python ni mensaje: el proceso muere.
Con `faulthandler` activado hacia un archivo, el siguiente cierre deja la pila de Python de todos los hilos
en `registros/visor_<modelo>.log`, junto a la aplicación, para poder analizarlo.
"""
import faulthandler
import os
import sys
import time
from pathlib import Path

RAIZ_APP = Path(__file__).resolve().parents[2]
_archivo = None  # se conserva abierto mientras viva el proceso: faulthandler escribe en su descriptor


def carpeta_registros():
    return Path(os.environ.get("CAVIDADES_LOG_DIR") or RAIZ_APP / "registros")


def activar_faulthandler(modelo):
    """Activa faulthandler escribiendo en `registros/visor_<modelo>.log`; devuelve la ruta o None si no se pudo.

    Nunca interrumpe el arranque del visor: si la carpeta no se puede escribir, usa la salida de errores.
    """
    global _archivo
    try:
        carpeta = carpeta_registros()
        carpeta.mkdir(parents=True, exist_ok=True)
        ruta = carpeta / f"visor_{modelo or 'general'}.log"
        _archivo = open(ruta, "a", buffering=1, encoding="utf-8")
        _archivo.write(f"--- inicio {time.strftime('%Y-%m-%d %H:%M:%S')} pid={os.getpid()} python={sys.version.split()[0]}\n")
        faulthandler.enable(file=_archivo, all_threads=True)
        return ruta
    except Exception:  # sin disco o sin permisos
        if sys.stderr is not None:
            faulthandler.enable(all_threads=True)
        return None


def registrar_excepcion(contexto, exc):
    """Deja constancia de una excepción capturada (sin cerrar el visor) en el registro y en stderr."""
    import traceback
    texto = f"[{time.strftime('%H:%M:%S')}] excepción en {contexto}: {type(exc).__name__}: {exc}\n" \
            + "".join(traceback.format_exception(type(exc), exc, exc.__traceback__))
    if _archivo is not None:
        try:
            _archivo.write(texto)
        except Exception:
            pass
    if sys.stderr is not None:
        sys.stderr.write(texto)
