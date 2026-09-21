CREATE TABLE IF NOT EXISTS personas (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(255),
    apellido_paterno VARCHAR(255),
    apellido_materno VARCHAR(255)
);
