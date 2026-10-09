"""Sistema de coordenadas y referencia corporal común (contrato §2 del prompt).

Lo importan el script de Blender, el visor VTK y las pruebas. Todas las
funciones aceptan escalares o arreglos de numpy.

Convenciones:
    Unidades en metros. Caballo adulto de referencia: ~500 kg, alzada ~1,60 m.
    +X = craneal, +Y = izquierda, +Z = dorsal (mano derecha).
    Origen: X = 0 en la abertura pélvica craneal; Y = 0 en el plano medio;
    Z = 0 en el suelo de la cavidad a la altura del xifoides.
"""
import numpy as np

# Exponente de la superelipse de la sección transversal de la envolvente.
EXPONENTE_SECCION = 2.4

# Tabla de la envolvente interna: x, semiancho W, suelo zv, techo zd, región.
TABLA_ENVOLVENTE = [
    (1.62, 0.09, 0.22, 0.52, "Abertura torácica craneal"),
    (1.50, 0.17, 0.07, 0.62, "Tórax craneal"),
    (1.35, 0.23, 0.02, 0.68, "Tórax (corazón)"),
    (1.15, 0.28, 0.00, 0.71, "Abdomen intratorácico"),
    (0.90, 0.31, -0.04, 0.71, "Abdomen craneal"),
    (0.60, 0.31, -0.03, 0.68, "Abdomen medio"),
    (0.35, 0.26, 0.06, 0.62, "Abdomen caudal"),
    (0.18, 0.16, 0.24, 0.54, "Abertura pélvica"),
    (0.05, 0.12, 0.28, 0.50, "Pelvis"),
]
# np.interp exige abscisas crecientes: se invierte la tabla.
_TX = np.array([f[0] for f in TABLA_ENVOLVENTE][::-1])
_TW = np.array([f[1] for f in TABLA_ENVOLVENTE][::-1])
_TZV = np.array([f[2] for f in TABLA_ENVOLVENTE][::-1])
_TZD = np.array([f[3] for f in TABLA_ENVOLVENTE][::-1])
X_MIN_ENVOLVENTE = float(_TX[0])
X_MAX_ENVOLVENTE = float(_TX[-1])

# Límite caudal de la cavidad abdominal (abertura pélvica craneal).
X_ABERTURA_PELVICA = 0.18

# Líneas topográficas de referencia de la pared lateral (altura z, en m).
LINEAS_TOPOGRAFICAS = {
    "tuberosidad_coxal": 0.62,
    "punta_del_hombro": 0.45,
    "olecranon": 0.10,
}

NUM_COSTILLAS = 18
NUM_COSTILLAS_ESTERNALES = 8


# ----------------------------------------------------------------------------
# Costillas y espacios intercostales
# ----------------------------------------------------------------------------
def x_costilla(i):
    """Coordenada x de la costilla i (1…18) proyectada a media altura."""
    return 1.60 - 0.05 * (np.asarray(i, dtype=float) - 1.0)


def x_eic(n):
    """Coordenada x del espacio intercostal n (entre las costillas n y n+1)."""
    return (x_costilla(n) + x_costilla(np.asarray(n) + 1)) / 2.0


def eic_de_x(x):
    """Inverso aproximado de x_eic: número (real) de espacio intercostal en x."""
    return (1.60 - np.asarray(x, dtype=float)) / 0.05 + 0.5


# ----------------------------------------------------------------------------
# Envolvente interna de la cavidad
# ----------------------------------------------------------------------------
def envolvente(x):
    """Devuelve (W, zv, zd) interpolados linealmente en x (se satura en los extremos)."""
    x = np.asarray(x, dtype=float)
    return np.interp(x, _TX, _TW), np.interp(x, _TX, _TZV), np.interp(x, _TX, _TZD)


def centro_y_semialtura(x):
    """Centro vertical zc y semialtura hz de la sección en x."""
    _, zv, zd = envolvente(x)
    return (zv + zd) / 2.0, (zd - zv) / 2.0


def valor_envolvente(x, y, z, margen=0.0):
    """Valor de la superelipse: <= 1 dentro. `margen` reduce W y hz (m)."""
    w, _, _ = envolvente(x)
    zc, hz = centro_y_semialtura(x)
    w = np.maximum(w - margen, 1e-4)
    hz = np.maximum(hz - margen, 1e-4)
    e = EXPONENTE_SECCION
    return np.abs(np.asarray(y) / w) ** e + np.abs((np.asarray(z) - zc) / hz) ** e


def dentro_envolvente(x, y, z, margen=0.0):
    """True si el punto está dentro de la envolvente (con margen opcional)."""
    x = np.asarray(x, dtype=float)
    dentro_x = (x >= X_MIN_ENVOLVENTE) & (x <= X_MAX_ENVOLVENTE)
    return dentro_x & (valor_envolvente(x, y, z, margen) <= 1.0)


def distancia_envolvente(x, y, z):
    """Distancia con signo aproximada a la pared (negativa dentro, en m).

    Se usa como campo implícito: escala el exceso de la superelipse por el
    menor semieje de la sección.
    """
    w, _, _ = envolvente(x)
    _, hz = centro_y_semialtura(x)
    v = valor_envolvente(x, y, z)
    return (v ** (1.0 / EXPONENTE_SECCION) - 1.0) * np.minimum(w, hz)


def proyectar_dentro_envolvente(x, y, z, margen=0.01):
    """Escala (y, z - zc) hacia el centro para que el punto quede dentro con margen.

    Devuelve (y, z) corregidos; los puntos ya interiores no cambian.
    """
    y = np.asarray(y, dtype=float)
    z = np.asarray(z, dtype=float)
    zc, _ = centro_y_semialtura(x)
    v = valor_envolvente(x, y, z, margen)
    factor = np.where(v > 1.0, v ** (-1.0 / EXPONENTE_SECCION), 1.0)
    return y * factor, zc + (z - zc) * factor


# ----------------------------------------------------------------------------
# Diafragma: superficie x = f(y, z)
# ----------------------------------------------------------------------------
DIAFRAGMA_FLECHA = 0.28       # altura de la cúpula sobre el plano base (m)
DIAFRAGMA_RY = 0.26           # semieje transversal de la cúpula
DIAFRAGMA_Z0 = 0.30           # altura del vértice de la cúpula
DIAFRAGMA_RZ_VENTRAL = 0.30
DIAFRAGMA_RZ_DORSAL = 0.32


def x_base_diafragma(z):
    """Plano base oblicuo: ventral en el xifoides (~costilla 8), dorsal en ~costilla 18."""
    return 1.25 - (np.asarray(z, dtype=float) / 0.70) * 0.50


def _q_cupula(y, z):
    z = np.asarray(z, dtype=float)
    rz = np.where(z < DIAFRAGMA_Z0, DIAFRAGMA_RZ_VENTRAL, DIAFRAGMA_RZ_DORSAL)
    return (np.asarray(y, dtype=float) / DIAFRAGMA_RY) ** 2 + ((z - DIAFRAGMA_Z0) / rz) ** 2


def x_diafragma(y, z):
    """Coordenada x de la superficie diafragmática en (y, z)."""
    return x_base_diafragma(z) + DIAFRAGMA_FLECHA * np.maximum(0.0, 1.0 - _q_cupula(y, z))


def gradiente_diafragma(y, z):
    """Derivadas parciales (dx/dy, dx/dz) de la superficie diafragmática."""
    y = np.asarray(y, dtype=float)
    z = np.asarray(z, dtype=float)
    rz = np.where(z < DIAFRAGMA_Z0, DIAFRAGMA_RZ_VENTRAL, DIAFRAGMA_RZ_DORSAL)
    activo = (_q_cupula(y, z) < 1.0).astype(float)
    dfdy = -DIAFRAGMA_FLECHA * activo * 2.0 * y / DIAFRAGMA_RY ** 2
    dfdz = -0.50 / 0.70 - DIAFRAGMA_FLECHA * activo * 2.0 * (z - DIAFRAGMA_Z0) / rz ** 2
    return dfdy, dfdz


def normal_diafragma(y, z):
    """Normal unitaria del diafragma orientada hacia craneal (+X), forma (..., 3)."""
    dfdy, dfdz = gradiente_diafragma(y, z)
    n = np.stack(np.broadcast_arrays(np.ones_like(dfdy), -dfdy, -dfdz), axis=-1)
    return n / np.linalg.norm(n, axis=-1, keepdims=True)


def vertice_diafragma():
    """Vértice de la cúpula (punto más craneal): (x, y, z)."""
    return float(x_diafragma(0.0, DIAFRAGMA_Z0)), 0.0, DIAFRAGMA_Z0


# Aberturas del diafragma: (y, z) y radio del orificio. La x se calcula.
ABERTURAS_DIAFRAGMA = {
    "hiato_esofagico": (0.02, 0.57, 0.022),
    "hiato_aortico": (0.01, 0.64, 0.030),
    "foramen_vena_cava": (-0.04, 0.42, 0.027),
}


def punto_abertura(nombre):
    """Punto (x, y, z) de una abertura del diafragma sobre la superficie."""
    y, z, _ = ABERTURAS_DIAFRAGMA[nombre]
    return float(x_diafragma(y, z)), y, z


# ----------------------------------------------------------------------------
# Pulmón: borde basal (receso costodiafragmático)
# ----------------------------------------------------------------------------
def x_borde_basal_pulmon(z):
    """Borde basal del pulmón: recta de (z=0.08, x=1.30) a (z=0.65, x=0.85)."""
    z = np.asarray(z, dtype=float)
    return 1.30 + (z - 0.08) / (0.65 - 0.08) * (0.85 - 1.30)


# ----------------------------------------------------------------------------
# Clasificación de puntos por cavidad
# ----------------------------------------------------------------------------
def cavidad_de_punto(x, y, z):
    """Cavidad a la que pertenece un punto: 'toracica', 'abdominal', 'pelvica' o 'exterior'.

    La región inguinal no es una cavidad: se identifica por catálogo (pared
    ventral caudal y escroto), por eso no aparece aquí.
    """
    x = float(x)
    if not bool(dentro_envolvente(x, y, z)):
        return "exterior"
    if x < X_ABERTURA_PELVICA:
        return "pelvica"
    if x > float(x_diafragma(y, z)):
        return "toracica"
    return "abdominal"


def recortar_a_cavidad(puntos, cavidad, margen=0.01):
    """Corrige puntos (N, 3) para que queden dentro de su cavidad.

    - Envolvente: escala (y, z) hacia el centro de la sección.
    - Abdominal: x <= x_diafragma - margen.
    - Torácica: x >= x_diafragma + margen.
    Devuelve una copia corregida.
    """
    p = np.array(puntos, dtype=float, copy=True)
    x = np.clip(p[:, 0], X_MIN_ENVOLVENTE, X_MAX_ENVOLVENTE)
    p[:, 1], p[:, 2] = proyectar_dentro_envolvente(x, p[:, 1], p[:, 2], margen)
    xd = x_diafragma(p[:, 1], p[:, 2])
    if cavidad == "abdominal":
        p[:, 0] = np.minimum(p[:, 0], xd - margen)
    elif cavidad == "toracica":
        p[:, 0] = np.maximum(p[:, 0], xd + margen)
    return p
