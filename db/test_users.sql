-- Script para crear o corregir usuarios de prueba
INSERT INTO roles (nombre) VALUES ('ADMINISTRADOR'), ('PROFESOR'), ('ESTUDIANTE') ON CONFLICT (nombre) DO NOTHING;

-- Usuario: admin (Pass: admin123)
INSERT INTO usuarios (username, email, password_hash, activo) 
VALUES ('admin', 'admin@test.com', '$2a$12$jPSFusRDqV7x2l/kKUIg6OU9WCA6sZS43XYXoroqDwk.5oJTE1l1K', true)
ON CONFLICT (username) DO UPDATE SET
  email = EXCLUDED.email,
  password_hash = EXCLUDED.password_hash,
  activo = EXCLUDED.activo;
INSERT INTO usuario_roles (usuario_id, rol_id) 
SELECT u.id, r.id FROM usuarios u, roles r 
WHERE u.username = 'admin' AND r.nombre = 'ADMINISTRADOR'
ON CONFLICT DO NOTHING;

-- Usuario: profesor (Pass: profesor123)
INSERT INTO usuarios (username, email, password_hash, activo) 
VALUES ('profesor', 'profesor@test.com', '$2a$12$DOOfzG09GEseVPE18SuMT.0ORDmK.mpVsADxpmI8nrcDiEaG6rDw.', true)
ON CONFLICT (username) DO UPDATE SET
  email = EXCLUDED.email,
  password_hash = EXCLUDED.password_hash,
  activo = EXCLUDED.activo;
INSERT INTO usuario_roles (usuario_id, rol_id) 
SELECT u.id, r.id FROM usuarios u, roles r 
WHERE u.username = 'profesor' AND r.nombre = 'PROFESOR'
ON CONFLICT DO NOTHING;

-- Usuario: juan (Pass: juan123)
INSERT INTO usuarios (username, email, password_hash, activo) 
VALUES ('juan', 'juan@test.com', '$2a$12$cVnQ.AfzybVueipMAKMgV.f.6C0HEYwZRmu36iLSw43kQh/LmJCm6', true)
ON CONFLICT (username) DO UPDATE SET
  email = EXCLUDED.email,
  password_hash = EXCLUDED.password_hash,
  activo = EXCLUDED.activo;
INSERT INTO usuario_roles (usuario_id, rol_id) 
SELECT u.id, r.id FROM usuarios u, roles r 
WHERE u.username = 'juan' AND r.nombre = 'ESTUDIANTE'
ON CONFLICT DO NOTHING;

-- Usuario: maria (Pass: maria123)
INSERT INTO usuarios (username, email, password_hash, activo) 
VALUES ('maria', 'maria@test.com', '$2a$12$lNut3kvN1RV4TFWU/258beeiExtJDwckc4OLGAeMWTjgGpMAbdo1C', true)
ON CONFLICT (username) DO UPDATE SET
  email = EXCLUDED.email,
  password_hash = EXCLUDED.password_hash,
  activo = EXCLUDED.activo;
INSERT INTO usuario_roles (usuario_id, rol_id) 
SELECT u.id, r.id FROM usuarios u, roles r 
WHERE u.username = 'maria' AND r.nombre = 'ESTUDIANTE'
ON CONFLICT DO NOTHING;

-- Materia de prueba: Anatomia Veterinaria Equina I
INSERT INTO cursos (nombre, descripcion, profesor_id)
SELECT 'Anatomia Veterinaria Equina I', 'Curso practico de anatomia macroscopica y sistemas organicos del equino con apoyo de simulacion tridimensional.', u.id
FROM usuarios u WHERE u.username = 'profesor'
AND NOT EXISTS (SELECT 1 FROM cursos WHERE nombre = 'Anatomia Veterinaria Equina I');

-- Matricular a estudiantes de prueba (juan y maria)
INSERT INTO estudiante_materias (estudiante_id, curso_id)
SELECT u.id, c.id
FROM usuarios u, cursos c
WHERE u.username IN ('juan', 'maria') AND c.nombre = 'Anatomia Veterinaria Equina I'
ON CONFLICT DO NOTHING;

-- Simulaciones 3D de ejemplo vinculadas a la materia
INSERT INTO simulaciones (curso_id, nombre, descripcion, configuracion)
SELECT c.id, 'Practica 3D: Sistema Cardiovascular Equino',
       'Exploracion interactiva del corazon, arteria aorta, vena cava y red vascular periferica del caballo.',
       '{"habilitado": true, "tema": "cardiovascular_equino", "nivel": "intermedio", "script3d": "sistema_cardiovascular.py", "tiempoMinutos": 30, "objetivos": ["Identificar cavidades cardiacas", "Reconocer recorrido aortico y retorno venoso"], "instrucciones": ["Inicia el visor 3D", "Haz clic en cada estructura para ver su nombre y funcion", "Toma capturas para tu reporte"], "escenario": {"titulo": "Morfologia y Hemodinamica Equina", "descripcion": "Estudio visual tridimensional guiado"}}'::jsonb
FROM cursos c WHERE c.nombre = 'Anatomia Veterinaria Equina I'
AND NOT EXISTS (SELECT 1 FROM simulaciones s WHERE s.curso_id = c.id AND s.nombre = 'Practica 3D: Sistema Cardiovascular Equino');

INSERT INTO simulaciones (curso_id, nombre, descripcion, configuracion)
SELECT c.id, 'Practica 3D: Sistema Oseo y Biomecanica',
       'Estudio osteologico del esqueleto axial y apendicular del equino.',
       '{"habilitado": true, "tema": "osteologia_equina", "nivel": "basico", "script3d": "sistema_oseo.py", "tiempoMinutos": 25, "objetivos": ["Diferenciar huesos de la cana y falanges", "Identificar vertebras cervicales y toracicas"], "instrucciones": ["Gira la camara 360 grados", "Localiza los sesamoideos y hueso navicular"], "escenario": {"titulo": "Esqueleto del Equino", "descripcion": "Identificacion de piezas oseas clave"}}'::jsonb
FROM cursos c WHERE c.nombre = 'Anatomia Veterinaria Equina I'
AND NOT EXISTS (SELECT 1 FROM simulaciones s WHERE s.curso_id = c.id AND s.nombre = 'Practica 3D: Sistema Oseo y Biomecanica');

