with open('aplicacion_tres_d/aplicacion_tres_d/piel_caballo.py', 'r', encoding='utf-8') as f:
    text = f.read().replace('\r\n', '\n')

old_json = 'json_file = BASE_DIR / "sistema_oseo.json"'
new_json = 'json_file = BASE_DIR / "piel_caballo.json"'
assert old_json in text, 'old_json not found'
text = text.replace(old_json, new_json, 1)

old_title = 'self.setWindowTitle("Visor GLTF - Sistema Óseo")'
new_title = 'self.setWindowTitle("Visor GLTF - Piel del Caballo")'
assert old_title in text, 'old_title not found'
text = text.replace(old_title, new_title, 1)

with open('aplicacion_tres_d/aplicacion_tres_d/piel_caballo.py', 'w', encoding='utf-8') as f:
    f.write(text)
print('piel_caballo.py updated successfully!')
