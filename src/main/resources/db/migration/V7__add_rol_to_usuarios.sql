ALTER TABLE usuarios ADD COLUMN rol VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER';

CREATE INDEX idx_usuarios_rol ON usuarios(rol);

