"""Primitivas geométricas del modelado procedural (solo numpy, sin bpy).

Contiene:
- Curvas Catmull-Rom remuestreadas por longitud de arco y marcos de transporte paralelo.
- Mallas de tubo con radio variable, haustras, tenias y tapas redondeadas.
- Campos implícitos (distancias aproximadas) y extracción de superficie
  por «surface nets» sobre una rejilla regular.
- Suavizado de Taubin y Laplaciano sobre mallas arbitrarias.
- Relajación de líneas medias para separar tubos que se interpenetran.

Todas las funciones son deterministas.
"""
import numpy as np


# ============================================================================
# Curvas
# ============================================================================
def catmull_rom(puntos, paso=0.01, muestras_por_tramo=64):
    """Curva Catmull-Rom uniforme que pasa por `puntos`, remuestreada cada `paso` m.

    Devuelve (curva (N, 3), s (N,) longitud de arco, s_control (M,) arco de cada punto de control).
    """
    p = np.asarray(puntos, dtype=float)
    if len(p) == 1:
        return p.copy(), np.zeros(1), np.zeros(1)
    ext = np.vstack([2 * p[0] - p[1], p, 2 * p[-1] - p[-2]])
    t = np.linspace(0.0, 1.0, muestras_por_tramo, endpoint=False)[:, None]
    tramos = []
    for i in range(len(p) - 1):
        p0, p1, p2, p3 = ext[i], ext[i + 1], ext[i + 2], ext[i + 3]
        tramos.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t ** 2
                             + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    densa = np.vstack(tramos + [p[-1:]])
    s_densa = np.concatenate([[0.0], np.cumsum(np.linalg.norm(np.diff(densa, axis=0), axis=1))])
    s_control = s_densa[np.arange(len(p)) * muestras_por_tramo]
    return remuestrear(densa, paso, s_densa) + (s_control,)


def remuestrear(polilinea, paso, s=None):
    """Remuestrea una polilínea a espaciado uniforme. Devuelve (curva, s)."""
    poli = np.asarray(polilinea, dtype=float)
    if s is None:
        s = np.concatenate([[0.0], np.cumsum(np.linalg.norm(np.diff(poli, axis=0), axis=1))])
    largo = s[-1]
    n = max(2, int(np.ceil(largo / paso)) + 1)
    s_nueva = np.linspace(0.0, largo, n)
    curva = np.stack([np.interp(s_nueva, s, poli[:, k]) for k in range(3)], axis=1)
    return curva, s_nueva


def longitud(polilinea):
    return float(np.sum(np.linalg.norm(np.diff(np.asarray(polilinea), axis=0), axis=1)))


def suavizar_polilinea(p, iteraciones=10, alfa=0.3, fijos=(0, -1)):
    """Suavizado Laplaciano 1D manteniendo fijos los extremos."""
    p = np.array(p, dtype=float, copy=True)
    for _ in range(iteraciones):
        m = p.copy()
        m[1:-1] = p[1:-1] + alfa * (0.5 * (p[:-2] + p[2:]) - p[1:-1])
        for f in fijos:
            m[f] = p[f]
        p = m
    return p


def marcos_transporte(curva):
    """Tangentes, normales y binormales por transporte paralelo (sin torsión arbitraria)."""
    c = np.asarray(curva, dtype=float)
    t = np.gradient(c, axis=0)
    t /= np.linalg.norm(t, axis=1, keepdims=True) + 1e-12
    ref = np.array([0.0, 0.0, 1.0]) if abs(t[0, 2]) < 0.9 else np.array([0.0, 1.0, 0.0])
    n = np.zeros_like(c)
    n[0] = np.cross(t[0], np.cross(ref, t[0]))
    n[0] /= np.linalg.norm(n[0])
    for i in range(1, len(c)):
        v = n[i - 1] - np.dot(n[i - 1], t[i]) * t[i]
        nv = np.linalg.norm(v)
        n[i] = v / nv if nv > 1e-9 else n[i - 1]
    b = np.cross(t, n)
    return t, n, b


def perfil_radios(radios, s, s_control):
    """Radio a lo largo del arco `s`.

    - escalar: constante;
    - tantos valores como puntos de control: interpolado en el arco de cada punto;
    - otro número de valores: repartidos uniformemente a lo largo de la longitud.
    """
    r = np.atleast_1d(np.asarray(radios, dtype=float))
    if r.size == 1:
        return np.full_like(s, r[0])
    if s_control is not None and r.size == len(s_control):
        return np.interp(s, s_control, r)
    return np.interp(s, np.linspace(0.0, s[-1], r.size), r)


# ============================================================================
# Mallas de tubo
# ============================================================================
def modulacion_haustras(s, theta, amplitud=0.08, periodo=0.08, n_tenias=0,
                        profundidad_tenia=0.07, ancho_tenia=0.16, anillos=None):
    """Factor multiplicativo del radio: haustras (senoide), tenias (surcos) y anillos traqueales."""
    s = np.asarray(s)[:, None]
    th = np.asarray(theta)[None, :]
    f = np.ones((s.shape[0], th.shape[1]))
    if amplitud:
        f = f * (1.0 + amplitud * np.sin(2 * np.pi * s / periodo))
    if n_tenias:
        surco = np.zeros_like(th, dtype=float)
        for k in range(n_tenias):
            d = np.angle(np.exp(1j * (th - 2 * np.pi * k / n_tenias)))
            surco = surco + np.exp(-(d / ancho_tenia) ** 2)
        # En la tenia no hay saculación: se atenúa la haustra y se hunde la pared.
        f = 1.0 + (f - 1.0) * (1.0 - surco) - profundidad_tenia * surco
    if anillos:
        amp, per = anillos
        f = f * (1.0 + amp * np.cos(2 * np.pi * s / per))
    return f


def malla_tubo(curva, radio, factor=None, n_lados=24, tapas=True, anillos_tapa=5):
    """Tubo cerrado alrededor de `curva`.

    radio: arreglo (N,) con el radio en cada punto; factor: (N, n_lados) multiplicativo opcional.
    Devuelve (vértices (V, 3), caras: lista de tuplas de índices).
    """
    c = np.asarray(curva, dtype=float)
    t, n, b = marcos_transporte(c)
    theta = np.linspace(0.0, 2 * np.pi, n_lados, endpoint=False)
    r = np.asarray(radio, dtype=float)[:, None] * (1.0 if factor is None else factor)
    cos, sin = np.cos(theta)[None, :, None], np.sin(theta)[None, :, None]
    anillos = c[:, None, :] + r[:, :, None] * (cos * n[:, None, :] + sin * b[:, None, :])
    verts = [anillos.reshape(-1, 3)]
    N = len(c)
    caras = []
    for i in range(N - 1):
        a, bb = i * n_lados, (i + 1) * n_lados
        for j in range(n_lados):
            j2 = (j + 1) % n_lados
            caras.append((a + j, a + j2, bb + j2, bb + j))
    if tapas:
        base = N * n_lados
        for extremo, signo in ((0, -1.0), (N - 1, 1.0)):
            ring_prev = np.arange(extremo * n_lados, extremo * n_lados + n_lados)
            r_ext = r[extremo]
            for k in range(1, anillos_tapa + 1):
                phi = 0.5 * np.pi * k / (anillos_tapa + 1)
                ring = (c[extremo] + signo * t[extremo] * np.mean(r_ext) * np.sin(phi)
                        + (r_ext * np.cos(phi))[:, None] * (np.cos(theta)[:, None] * n[extremo]
                                                            + np.sin(theta)[:, None] * b[extremo]))
                verts.append(ring)
                idx = np.arange(base, base + n_lados)
                for j in range(n_lados):
                    j2 = (j + 1) % n_lados
                    q = (ring_prev[j], ring_prev[j2], idx[j2], idx[j])
                    caras.append(q if signo > 0 else q[::-1])
                ring_prev, base = idx, base + n_lados
            polo = c[extremo] + signo * t[extremo] * np.mean(r_ext)
            verts.append(polo[None, :])
            for j in range(n_lados):
                j2 = (j + 1) % n_lados
                tri = (ring_prev[j], ring_prev[j2], base)
                caras.append(tri if signo > 0 else tri[::-1])
            base += 1
    return np.vstack(verts), caras


def unir_mallas(partes):
    """Concatena varias (verts, caras) en una sola malla."""
    verts, caras, off = [], [], 0
    for v, f in partes:
        verts.append(np.asarray(v))
        caras.extend(tuple(i + off for i in cara) for cara in f)
        off += len(v)
    return np.vstack(verts), caras


# ============================================================================
# Campos implícitos (valores < 0 dentro; magnitud aproximada en metros)
# ============================================================================
def sdf_elipsoide(p, centro, semiejes, rotacion=None):
    """Distancia aproximada a un elipsoide. `rotacion`: matriz 3x3 local->mundo."""
    q = np.asarray(p) - np.asarray(centro)
    if rotacion is not None:
        q = q @ np.asarray(rotacion)
    a = np.asarray(semiejes, dtype=float)
    k = np.linalg.norm(q / a, axis=-1)
    return (k - 1.0) * np.min(a)


def sdf_capsulas(p, curva, radios, bloque=20000):
    """Distancia a una cadena de cápsulas (polilínea con radio interpolado)."""
    p = np.asarray(p, dtype=float)
    c = np.asarray(curva, dtype=float)
    r = np.broadcast_to(np.asarray(radios, dtype=float), (len(c),))
    a, b = c[:-1], c[1:]
    ab = b - a
    l2 = np.maximum(np.sum(ab * ab, axis=1), 1e-12)
    salida = np.empty(len(p))
    for i in range(0, len(p), bloque):
        q = p[i:i + bloque, None, :]
        t = np.clip(np.sum((q - a) * ab, axis=2) / l2, 0.0, 1.0)
        d = np.linalg.norm(q - (a + t[..., None] * ab), axis=2) - (r[:-1] + t * (r[1:] - r[:-1]))
        salida[i:i + bloque] = d.min(axis=1)
    return salida


def sdf_caja(p, minimo, maximo):
    """Distancia aproximada a una caja alineada con los ejes."""
    p = np.asarray(p)
    c = (np.asarray(minimo) + np.asarray(maximo)) / 2.0
    h = (np.asarray(maximo) - np.asarray(minimo)) / 2.0
    q = np.abs(p - c) - h
    return np.linalg.norm(np.maximum(q, 0.0), axis=-1) + np.minimum(np.max(q, axis=-1), 0.0)


def sdf_toro(p, centro, eje, radio_mayor, radio_menor, escala_plano=(1.0, 1.0)):
    """Toroide (opcionalmente elíptico en su plano) con normal `eje`."""
    e = np.asarray(eje, dtype=float)
    e /= np.linalg.norm(e)
    u = np.cross(e, [0.0, 0.0, 1.0])
    if np.linalg.norm(u) < 1e-6:
        u = np.cross(e, [1.0, 0.0, 0.0])
    u /= np.linalg.norm(u)
    v = np.cross(e, u)
    q = np.asarray(p) - np.asarray(centro)
    a, b, h = q @ u, q @ v, q @ e
    ra, rb = radio_mayor * escala_plano[0], radio_mayor * escala_plano[1]
    k = np.sqrt((a / ra) ** 2 + (b / rb) ** 2)
    radial = (k - 1.0) * 0.5 * (ra + rb)
    return np.sqrt(radial ** 2 + h ** 2) - radio_menor


def smin(a, b, k=0.02):
    """Unión suave (mínimo polinómico)."""
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0.0, 1.0)
    return b * (1 - h) + a * h - k * h * (1 - h)


def desenfocar(campo, sigma_celdas):
    """Desenfoque gaussiano separable en 3D (bordes replicados)."""
    if sigma_celdas <= 0:
        return campo
    r = max(1, int(np.ceil(2.5 * sigma_celdas)))
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma_celdas) ** 2)
    k /= k.sum()
    f = campo
    for eje in range(3):
        pad = [(0, 0)] * 3
        pad[eje] = (r, r)
        g = np.pad(f, pad, mode="edge")
        acc = np.zeros_like(f)
        n = f.shape[eje]
        for i, w in enumerate(k):
            sl = [slice(None)] * 3
            sl[eje] = slice(i, i + n)
            acc += w * g[tuple(sl)]
        f = acc
    return f


def rejilla(minimo, maximo, h):
    """Coordenadas de una rejilla regular con una celda de holgura en cada borde."""
    mn = np.asarray(minimo, dtype=float) - 2 * h
    mx = np.asarray(maximo, dtype=float) + 2 * h
    ejes = [np.arange(mn[k], mx[k] + h, h) for k in range(3)]
    return ejes


def superficie_por_campo(funcion, minimo, maximo, h, sigma=0.0):
    """Extrae la isosuperficie 0 de `funcion(p (N,3)) -> (N,)` con surface nets.

    Devuelve (vértices, caras cuadriláteras) cerradas y orientadas hacia fuera.
    """
    ex, ey, ez = rejilla(minimo, maximo, h)
    X, Y, Z = np.meshgrid(ex, ey, ez, indexing="ij")
    pts = np.stack([X.ravel(), Y.ravel(), Z.ravel()], axis=1)
    F = funcion(pts).reshape(X.shape)
    F = desenfocar(F, sigma)
    # Bordes de la rejilla forzados a «fuera» para cerrar la superficie.
    F[0, :, :] = F[-1, :, :] = F[:, 0, :] = F[:, -1, :] = F[:, :, 0] = F[:, :, -1] = abs(h)
    return _surface_nets(F, np.array([ex[0], ey[0], ez[0]]), h)


def _surface_nets(F, origen, h):
    nx, ny, nz = F.shape
    dentro = F < 0
    esquinas = [(i, j, k) for i in (0, 1) for j in (0, 1) for k in (0, 1)]
    aristas = [(a, b) for a in range(8) for b in range(a + 1, 8)
               if sum(abs(esquinas[a][d] - esquinas[b][d]) for d in range(3)) == 1]
    vals = [F[i:nx - 1 + i, j:ny - 1 + j, k:nz - 1 + k] for i, j, k in esquinas]
    suma = np.zeros((nx - 1, ny - 1, nz - 1, 3))
    cuenta = np.zeros((nx - 1, ny - 1, nz - 1))
    for a, b in aristas:
        va, vb = vals[a], vals[b]
        cruza = (va < 0) != (vb < 0)
        t = np.where(cruza, va / np.where(cruza, va - vb, 1.0), 0.0)
        pa, pb = np.array(esquinas[a], float), np.array(esquinas[b], float)
        pos = pa + t[..., None] * (pb - pa)
        suma += np.where(cruza[..., None], pos, 0.0)
        cuenta += cruza
    activas = cuenta > 0
    indice = -np.ones(activas.shape, dtype=np.int64)
    indice[activas] = np.arange(int(activas.sum()))
    ci = np.argwhere(activas)
    verts = origen + h * (ci + suma[activas] / cuenta[activas][:, None])
    caras = []
    # Para cada arista de la rejilla con cambio de signo se crea un cuadrilátero
    # con las cuatro celdas que la comparten (orden con normal hacia fuera).
    defs = [
        (dentro[:-1, 1:-1, 1:-1], dentro[1:, 1:-1, 1:-1], (0, 1, 1),
         [(0, -1, -1), (0, 0, -1), (0, 0, 0), (0, -1, 0)]),
        (dentro[1:-1, :-1, 1:-1], dentro[1:-1, 1:, 1:-1], (1, 0, 1),
         [(-1, 0, -1), (-1, 0, 0), (0, 0, 0), (0, 0, -1)]),
        (dentro[1:-1, 1:-1, :-1], dentro[1:-1, 1:-1, 1:], (1, 1, 0),
         [(-1, -1, 0), (0, -1, 0), (0, 0, 0), (-1, 0, 0)]),
    ]
    for a, b, base, offs in defs:
        cambio = np.argwhere(a != b) + np.array(base)
        if len(cambio) == 0:
            continue
        # Si el punto inicial de la arista está dentro, la normal apunta hacia +eje.
        hacia_fuera = dentro[tuple(cambio.T)]
        quads = np.stack([indice[tuple((cambio + np.array(o)).T)] for o in offs], axis=1)
        quads[~hacia_fuera] = quads[~hacia_fuera][:, ::-1]
        validos = np.all(quads >= 0, axis=1)
        caras.extend(map(tuple, quads[validos]))
    return verts, caras


# ============================================================================
# Suavizado de mallas
# ============================================================================
def aristas_de_caras(caras):
    pares = set()
    for f in caras:
        for i in range(len(f)):
            a, b = f[i], f[(i + 1) % len(f)]
            pares.add((a, b) if a < b else (b, a))
    return np.array(sorted(pares), dtype=np.int64)


def suavizar_laplaciano(verts, aristas, iteraciones=2, lam=0.5, mu=None, mascara=None):
    """Laplaciano (mu=None) o Taubin (mu<0) con pesos uniformes.

    `mascara` (V,) bool opcional: solo se mueven los vértices marcados.
    """
    v = np.array(verts, dtype=float, copy=True)
    a, b = aristas[:, 0], aristas[:, 1]
    grado = np.bincount(np.concatenate([a, b]), minlength=len(v)).astype(float)[:, None]
    grado[grado == 0] = 1.0
    pasos = [lam] if mu is None else [lam, mu]
    for _ in range(iteraciones):
        for factor in pasos:
            suma = np.zeros_like(v)
            np.add.at(suma, a, v[b])
            np.add.at(suma, b, v[a])
            delta = suma / grado - v
            if mascara is not None:
                delta[~mascara] = 0.0
            v += factor * delta
    return v


# ============================================================================
# Relajación de líneas medias (separación de tubos)
# ============================================================================
def relajar_tubos(puntos, radios, prioridad, cadena, arco, fijos, restriccion,
                  obstaculos=(), excluir=None, holgura=0.006, iteraciones=200,
                  paso_max=0.008, alfa_suavizado=0.12, iteraciones_finales=30):
    """Separa esferas a lo largo de líneas medias que se interpenetran.

    puntos (N,3), radios (N,), prioridad (N,), cadena (N,) id de cadena continua,
    arco (N,) longitud de arco dentro de la cadena, fijos (N,) bool.
    restriccion(P, R) -> P corregido (cavidad). obstaculos: lista de
    (sdf(P)->(N,), prioridad). excluir(i_mask, j_mask) -> matriz bool de pares
    que no deben repelerse (uniones anatómicas).
    El punto de menor prioridad se desplaza; con igual prioridad, ambos.
    Las últimas `iteraciones_finales` no suavizan, para que al terminar se
    respeten las separaciones y los obstáculos.
    """
    P = np.array(puntos, dtype=float, copy=True)
    R = np.asarray(radios, dtype=float)
    pr = np.asarray(prioridad, dtype=float)
    misma = np.asarray(cadena)[:, None] == np.asarray(cadena)[None, :]
    vecinos = misma & (np.abs(np.asarray(arco)[:, None] - np.asarray(arco)[None, :])
                       < 2.5 * (R[:, None] + R[None, :]) + 0.04)
    valido = ~vecinos
    if excluir is not None:
        valido &= ~excluir
    np.fill_diagonal(valido, False)
    peso = np.where(pr[:, None] < pr[None, :], 1.0, np.where(pr[:, None] == pr[None, :], 0.5, 0.0))
    necesario = R[:, None] + R[None, :] + holgura
    movil = ~np.asarray(fijos, dtype=bool)
    # Índices de vecinos inmediatos en la misma cadena para el suavizado 1D.
    cad = np.asarray(cadena)
    interior = np.zeros(len(P), dtype=bool)
    interior[1:-1] = (cad[1:-1] == cad[:-2]) & (cad[1:-1] == cad[2:])
    for it in range(iteraciones + iteraciones_finales):
        alfa = alfa_suavizado if it < iteraciones else 0.0
        d = P[:, None, :] - P[None, :, :]
        dist = np.linalg.norm(d, axis=2) + 1e-9
        solape = np.where(valido, np.maximum(necesario - dist, 0.0), 0.0) * peso
        desp = np.einsum("ij,ijk->ik", solape / dist, d) * 0.5
        for sdf, prio_obs in obstaculos:
            dd = sdf(P) - R - holgura
            dentro = (dd < 0) & (pr < prio_obs)
            if np.any(dentro):
                g = _gradiente(sdf, P[dentro])
                desp[dentro] += -dd[dentro, None] * g
        norma = np.linalg.norm(desp, axis=1, keepdims=True)
        desp *= np.minimum(1.0, paso_max / np.maximum(norma, 1e-12))
        P[movil] += desp[movil]
        S = P.copy()
        S[1:-1] = P[1:-1] + alfa * (0.5 * (P[:-2] + P[2:]) - P[1:-1])
        P = np.where((interior & movil)[:, None], S, P)
        P[movil] = restriccion(P, R)[movil]
        if it >= iteraciones:  # fase final: los obstáculos prevalecen sobre la separación entre tubos
            for sdf, prio_obs in obstaculos:
                dd = sdf(P) - R - holgura
                dentro = (dd < 0) & (pr < prio_obs) & movil
                if np.any(dentro):
                    P[dentro] += -dd[dentro, None] * _gradiente(sdf, P[dentro])
    return P


def _gradiente(sdf, p, e=1e-3):
    g = np.zeros_like(p)
    for k in range(3):
        d = np.zeros(3)
        d[k] = e
        g[:, k] = (sdf(p + d) - sdf(p - d)) / (2 * e)
    return g / (np.linalg.norm(g, axis=1, keepdims=True) + 1e-12)


# ============================================================================
# Métricas
# ============================================================================
def area_malla(verts, caras):
    v = np.asarray(verts)
    total = 0.0
    for f in caras:
        p0 = v[f[0]]
        for i in range(1, len(f) - 1):
            total += 0.5 * np.linalg.norm(np.cross(v[f[i]] - p0, v[f[i + 1]] - p0))
    return total


def volumen_y_centroide(verts, tris):
    """Volumen (teorema de la divergencia) y centroide volumétrico de una malla triangular cerrada."""
    v = np.asarray(verts, dtype=float)
    t = np.asarray(tris, dtype=np.int64)
    a, b, c = v[t[:, 0]], v[t[:, 1]], v[t[:, 2]]
    vol6 = np.einsum("ij,ij->i", a, np.cross(b, c))
    vol = vol6.sum() / 6.0
    if abs(vol) < 1e-12:
        return 0.0, v.mean(axis=0)
    cen = ((a + b + c) * vol6[:, None]).sum(axis=0) / (24.0 * vol)
    return float(vol), cen
