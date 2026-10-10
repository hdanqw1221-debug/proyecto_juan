"""Diafragma: lámina sobre la superficie x = x_diafragma(y, z), recortada por la envolvente.

A diferencia de los demás órganos se construye como malla explícita (es una lámina fina):
    malla()        -> (vértices, caras) de la lámina cerrada (cara craneal, caudal y borde).
    uniones()      -> [(vértices, caras)] mallas cerradas que se suman con booleano (p. ej., pilares).
    cortadores()   -> (vértices, caras) mallas cerradas que se restan con booleano (aberturas).
El generador de Blender aplica los booleanos; el prototipador muestra las piezas sin aplicarlos.
"""
import numpy as np

from anatomia import referencia as ref
from blender import primitivas as P

GROSOR = 0.010


def malla():
    M_ang, K = 120, 26
    c_y, c_z = 0.0, 0.33
    ang = np.linspace(0.0, 2 * np.pi, M_ang, endpoint=False)
    largo = np.zeros(M_ang)
    for i, a in enumerate(ang):
        lo, hi = 0.0, 0.9
        for _ in range(40):
            mid = 0.5 * (lo + hi)
            y, z = c_y + mid * np.cos(a), c_z + mid * np.sin(a)
            dentro = ref.valor_envolvente(ref.x_diafragma(y, z), y, z, 0.002) <= 1.0
            lo, hi = (mid, hi) if dentro else (lo, mid)
        largo[i] = lo
    rho = (np.arange(1, K + 1) / K)[:, None] * largo[None, :]
    Y = np.concatenate([[c_y], (c_y + rho * np.cos(ang)).ravel()])
    Z = np.concatenate([[c_z], (c_z + rho * np.sin(ang)).ravel()])
    X = ref.x_diafragma(Y, Z)
    Pm = np.stack([X, Y, Z], axis=1)
    n = ref.normal_diafragma(Y, Z)
    sup, inf = Pm + 0.5 * GROSOR * n, Pm - 0.5 * GROSOR * n
    nv = len(Pm)
    verts = np.vstack([sup, inf])
    idx = lambda k, j: 1 + k * M_ang + (j % M_ang)  # noqa: E731
    caras = []
    for j in range(M_ang):
        caras.append((0, idx(0, j), idx(0, j + 1)))
        caras.append((nv, nv + idx(0, j + 1), nv + idx(0, j)))
    for k in range(K - 1):
        for j in range(M_ang):
            q = (idx(k, j), idx(k + 1, j), idx(k + 1, j + 1), idx(k, j + 1))
            caras.append(q)
            caras.append(tuple(nv + i for i in q[::-1]))
    for j in range(M_ang):
        a, b = idx(K - 1, j), idx(K - 1, j + 1)
        caras.append((a, nv + a, nv + b, b))
    return verts, caras


def uniones():
    return []


def cortadores():
    """Cilindros orientados según la normal local en cada abertura (hiatos aórtico y esofágico, foramen de la cava)."""
    cortes = []
    for nombre, (y, z, r) in ref.ABERTURAS_DIAFRAGMA.items():
        p0 = np.array(ref.punto_abertura(nombre))
        nn = ref.normal_diafragma(y, z)
        cortes.append(P.malla_tubo(np.array([p0 - 0.05 * nn, p0 + 0.05 * nn]), np.array([r, r]), n_lados=24,
                                   anillos_tapa=1))
    return P.unir_mallas(cortes)


def forma(objeto_id, p):
    """Distancia aproximada a la lámina (para vecinos que quieran apartarse de ella)."""
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    n = ref.normal_diafragma(y, z)
    d = np.abs((x - ref.x_diafragma(y, z)) * n[:, 0]) - 0.5 * GROSOR
    return np.maximum(d, ref.distancia_envolvente(x, y, z))
