"""Paleta de materiales por sistema (§4.6.5). Editable.

El diccionario PALETA no depende de Blender, de modo que el catálogo y el
visor lo pueden importar; `crear_material` sí requiere bpy.
"""

PALETA = {
    "respiratorio": "#E8A0A8",
    "arterial": "#C0392B",
    "venoso": "#2E5C9A",
    "corazon": "#A93226",
    "digestivo_tubular": "#D9A066",
    "estomago": "#C98B5A",
    "ciego_colon": "#B9875A",
    "higado": "#7B3F2A",
    "pancreas": "#E3C08D",
    "bazo": "#6E2C4F",
    "urinario": "#9C5B3C",
    "vejiga": "#E6C27A",
    "reproductor": "#D7A9C0",
    "serosa": "#D6CFC4",
    # Envolturas de cavidad (modelos inmersivos): pleura costal, peritoneo parietal y piel del escroto.
    "pleura": "#E6C3BC",
    "peritoneo": "#DDC5AE",
    "escroto": "#B98A7E",
    "referencia": "#888888",
    # Color de resaltado de la selección en el visor.
    "seleccion": "#FFD54F",
}


def hex_a_rgb(color_hex):
    """'#RRGGBB' -> (r, g, b) en 0…1."""
    c = color_hex.lstrip("#")
    return tuple(int(c[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


def crear_material(nombre, color_hex, opacidad=1.0):
    """Crea (o reutiliza) un material Principled BSDF con el color y la opacidad dados."""
    import bpy

    mat = bpy.data.materials.get(nombre) or bpy.data.materials.new(nombre)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    r, g, b = hex_a_rgb(color_hex)
    mat.diffuse_color = (r, g, b, opacidad)
    if bsdf is not None:
        bsdf.inputs["Base Color"].default_value = (r, g, b, 1.0)
        bsdf.inputs["Roughness"].default_value = 0.55
        bsdf.inputs["Alpha"].default_value = opacidad
    if opacidad < 1.0:
        mat.blend_method = "BLEND"
    return mat
