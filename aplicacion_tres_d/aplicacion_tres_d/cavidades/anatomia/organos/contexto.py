"""Utilidades comunes a los órganos y acceso a las formas de los vecinos (contexto).

El generador de Blender y el prototipador construyen un `Contexto` con las líneas medias relajadas
de los tubos digestivos; con él cada órgano puede tallar las impresiones de sus vecinos.
"""
import importlib

import numpy as np

from anatomia import especificacion as E
from anatomia import referencia as ref
from blender import primitivas as P

MARGEN_CAVIDAD = 0.01

# Módulo que modela cada estructura maciza (el resto son tubos de especificacion.TUBOS o cadenas relajadas).
MODULO_DE = {
    "TOR_CORAZON": "corazon", "TOR_PERICARDIO": "corazon",
    "TOR_PULMON_IZQ": "pulmones", "TOR_PULMON_DER": "pulmones",
    "ABD_HIGADO": "higado",
    "ABD_BAZO": "bazo",
    "ABD_PANCREAS": "pancreas",
    "ABD_RINON_DER": "rinones", "ABD_RINON_IZQ": "rinones",
    "ABD_ADRENAL_DER": "rinones", "ABD_ADRENAL_IZQ": "rinones",
    "PEL_VEJIGA": "vejiga",
    "ABD_ESTOMAGO": "estomago",
}


def modulo(objeto_id):
    """Módulo de órgano que modela `objeto_id` (o None si es un tubo)."""
    nombre = MODULO_DE.get(objeto_id)
    return importlib.import_module(f"anatomia.organos.{nombre}") if nombre else None


# ----------------------------------------------------------------------------
# Cavidad
# ----------------------------------------------------------------------------
def restriccion_cavidad(p, cavidad, margen=MARGEN_CAVIDAD):
    """Campo (> 0 fuera) que limita un órgano a su cavidad."""
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    c = ref.distancia_envolvente(x, y, z) + margen
    n = ref.normal_diafragma(y, z)
    d = (ref.x_diafragma(y, z) - x) * n[:, 0]
    if cavidad == "abdominal":
        c = np.maximum(c, margen - d)
    elif cavidad == "toracica":
        c = np.maximum(c, margen + d)
    return c


def recortar(V, modo, margen=MARGEN_CAVIDAD):
    """Empuja los vértices fuera de su cavidad hacia dentro (según la normal del diafragma)."""
    if modo is None:
        return V
    V = np.array(V, copy=True)
    for _ in range(3):
        x = np.clip(V[:, 0], ref.X_MIN_ENVOLVENTE, ref.X_MAX_ENVOLVENTE)
        V[:, 1], V[:, 2] = ref.proyectar_dentro_envolvente(x, V[:, 1], V[:, 2], margen)
        if modo in ("abdominal", "toracica"):
            xd = ref.x_diafragma(V[:, 1], V[:, 2])
            n = ref.normal_diafragma(V[:, 1], V[:, 2])
            d = (xd - V[:, 0]) * n[:, 0]  # > 0 caudal al diafragma
            if modo == "abdominal":
                falta = np.maximum(margen - d, 0.0)
                V -= falta[:, None] * n
            else:
                falta = np.maximum(margen + d, 0.0)
                V += falta[:, None] * n
    return V


# ----------------------------------------------------------------------------
# Tubos como campos
# ----------------------------------------------------------------------------
def curvas_de_spec(spec):
    salida = []
    for parte in spec["partes"]:
        c, s, sc = P.catmull_rom(parte["puntos"], paso=0.01)
        salida.append((c, P.perfil_radios(parte["radios"], s, sc)))
    return salida


def campo_tubos(partes_curvas):
    """SDF de la unión de varias cadenas de cápsulas [(curva, radios)]."""
    def f(p):
        return np.min([P.sdf_capsulas(p, c, r) for c, r in partes_curvas], axis=0)
    return f


def marco(t, n):
    """Matriz local->mundo con columnas (largo, ancho, grosor) a partir de una tangente y una normal."""
    t = t / np.linalg.norm(t)
    n = n - np.dot(n, t) * t
    n /= np.linalg.norm(n)
    return np.stack([t, np.cross(n, t), n], axis=1)


# ----------------------------------------------------------------------------
# Contexto
# ----------------------------------------------------------------------------
class Contexto:
    """Formas de los vecinos de un órgano.

    lineas: {objeto_id: (curva (N, 3), radios (N,))} líneas medias relajadas de los tubos digestivos.
    esofago_final: [(curva, radios)] del esófago prolongado hasta el cardias relajado.
    """

    def __init__(self, lineas=None, esofago_final=None):
        self.lineas = dict(lineas or {})
        self.esofago_final = esofago_final

    def curvas(self, objeto_id):
        """Líneas medias [(curva, radios)] de un tubo (relajadas si existen; si no, las de la especificación)."""
        if objeto_id in self.lineas:
            return [self.lineas[objeto_id]]
        if objeto_id == "TOR_ESOFAGO" and self.esofago_final:
            return self.esofago_final
        return curvas_de_spec(E.TUBOS[objeto_id])

    def sdf(self, objeto_id):
        """Forma base de cualquier estructura como función p -> (N,)."""
        mod = modulo(objeto_id)
        if objeto_id in self.lineas and (mod is None or not getattr(mod, "USAR_CAMPO", True)):
            from anatomia.organos import tubos  # tubo digestivo relajado, con sus haustras

            return lambda p: tubos.forma(objeto_id, p, self)
        if mod is None or not getattr(mod, "USAR_CAMPO", True):
            return campo_tubos(self.curvas(objeto_id))
        if getattr(mod, "FORMA_CON_CONTEXTO", False):
            return lambda p: mod.forma(objeto_id, p, self)
        return lambda p: mod.forma(objeto_id, p)

    def pieza(self, objeto_id):
        """Parámetros de mallado de una pieza (caja, h, sigma, recorte...)."""
        mod = modulo(objeto_id)
        return mod.piezas(self)[objeto_id] if hasattr(mod, "piezas") else mod.PIEZAS[objeto_id]
