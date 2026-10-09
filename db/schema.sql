-- Roles usados por la aplicacion: ADMINISTRADOR, PROFESOR y ESTUDIANTE
-- Nota: la columna profesor_id en cursos almacena al docente o administrador titular de la materia.

CREATE TABLE roles (
  id BIGSERIAL PRIMARY KEY,
  nombre VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE usuarios (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  email VARCHAR(120) NOT NULL UNIQUE,
  password_hash VARCHAR(200) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE usuario_roles (
  usuario_id BIGINT NOT NULL,
  rol_id BIGINT NOT NULL,
  PRIMARY KEY (usuario_id, rol_id),
  CONSTRAINT fk_ur_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  CONSTRAINT fk_ur_rol FOREIGN KEY (rol_id) REFERENCES roles(id) ON DELETE RESTRICT
);

CREATE TABLE cursos (
  id BIGSERIAL PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL,
  descripcion TEXT,
  profesor_id BIGINT NOT NULL,
  creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_curso_profesor FOREIGN KEY (profesor_id) REFERENCES usuarios(id) ON DELETE RESTRICT
);

CREATE INDEX idx_cursos_profesor ON cursos(profesor_id);

CREATE TABLE estudiante_materias (
  estudiante_id BIGINT NOT NULL,
  curso_id BIGINT NOT NULL,
  asignado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (estudiante_id, curso_id),
  CONSTRAINT fk_em_estudiante FOREIGN KEY (estudiante_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  CONSTRAINT fk_em_curso FOREIGN KEY (curso_id) REFERENCES cursos(id) ON DELETE CASCADE
);

CREATE INDEX idx_estudiante_materias_estudiante ON estudiante_materias(estudiante_id);
CREATE INDEX idx_estudiante_materias_curso ON estudiante_materias(curso_id);

CREATE TABLE examenes (
  id BIGSERIAL PRIMARY KEY,
  curso_id BIGINT NOT NULL,
  titulo VARCHAR(120) NOT NULL,
  descripcion TEXT,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  vence_en TIMESTAMP,
  limite_intentos INT NOT NULL DEFAULT 1,
  duracion_minutos INT NOT NULL DEFAULT 60,
  creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_examen_curso FOREIGN KEY (curso_id) REFERENCES cursos(id) ON DELETE CASCADE
);

-- Si la base de datos ya existe, ejecuta estos comandos manualmente:
-- ALTER TABLE examenes ADD COLUMN limite_intentos INT NOT NULL DEFAULT 1;
-- ALTER TABLE examenes ADD COLUMN duracion_minutos INT NOT NULL DEFAULT 60;

CREATE INDEX idx_examenes_curso ON examenes(curso_id);
CREATE INDEX idx_examenes_activo ON examenes(activo);
CREATE INDEX idx_examenes_vence_en ON examenes(vence_en);

CREATE TABLE preguntas (
  id BIGSERIAL PRIMARY KEY,
  examen_id BIGINT NOT NULL,
  enunciado TEXT NOT NULL,
  tipo VARCHAR(30) NOT NULL,
  puntaje NUMERIC(6,2) NOT NULL DEFAULT 1,
  imagen BYTEA,
  imagen_nombre VARCHAR(255),
  imagen_tipo VARCHAR(100),
  orden INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_preguntas_examen FOREIGN KEY (examen_id) REFERENCES examenes(id) ON DELETE CASCADE
);

-- Para bases existentes:
-- ALTER TABLE preguntas ADD COLUMN IF NOT EXISTS imagen BYTEA;
-- ALTER TABLE preguntas ADD COLUMN IF NOT EXISTS imagen_nombre VARCHAR(255);
-- ALTER TABLE preguntas ADD COLUMN IF NOT EXISTS imagen_tipo VARCHAR(100);
-- ALTER TABLE preguntas ADD COLUMN IF NOT EXISTS orden INT NOT NULL DEFAULT 0;

CREATE INDEX idx_preguntas_examen ON preguntas(examen_id);

CREATE TABLE respuestas (
  id BIGSERIAL PRIMARY KEY,
  pregunta_id BIGINT NOT NULL,
  contenido TEXT NOT NULL,
  correcta BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_respuestas_pregunta FOREIGN KEY (pregunta_id) REFERENCES preguntas(id) ON DELETE CASCADE
);

CREATE INDEX idx_respuestas_pregunta ON respuestas(pregunta_id);

CREATE TABLE intentos_examen (
  id BIGSERIAL PRIMARY KEY,
  examen_id BIGINT NOT NULL,
  estudiante_id BIGINT NOT NULL,
  fecha_inicio TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_fin TIMESTAMP,
  puntaje_total NUMERIC(6,2),
  CONSTRAINT fk_intentos_examen_examen FOREIGN KEY (examen_id) REFERENCES examenes(id) ON DELETE CASCADE,
  CONSTRAINT fk_intentos_examen_estudiante FOREIGN KEY (estudiante_id) REFERENCES usuarios(id) ON DELETE RESTRICT
);

CREATE INDEX idx_intentos_examen_examen ON intentos_examen(examen_id);
CREATE INDEX idx_intentos_examen_estudiante ON intentos_examen(estudiante_id);

CREATE TABLE simulaciones (
  id BIGSERIAL PRIMARY KEY,
  curso_id BIGINT NOT NULL,
  nombre VARCHAR(120) NOT NULL,
  descripcion TEXT,
  configuracion JSONB,
  creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_simulaciones_curso FOREIGN KEY (curso_id) REFERENCES cursos(id) ON DELETE CASCADE
);

CREATE INDEX idx_simulaciones_curso ON simulaciones(curso_id);

CREATE TABLE resultados (
  id BIGSERIAL PRIMARY KEY,
  intento_id BIGINT NOT NULL,
  detalle JSONB,
  generado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_resultados_intento FOREIGN KEY (intento_id) REFERENCES intentos_examen(id) ON DELETE CASCADE
);

CREATE INDEX idx_resultados_intento ON resultados(intento_id);

CREATE TABLE logs (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT,
  accion VARCHAR(120) NOT NULL,
  detalle TEXT,
  creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_logs_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL
);

CREATE INDEX idx_logs_usuario ON logs(usuario_id);
CREATE INDEX idx_logs_creado_en ON logs(creado_en);
