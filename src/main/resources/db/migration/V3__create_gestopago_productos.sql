CREATE TABLE IF NOT EXISTS gestopago_productos (
    id SERIAL PRIMARY KEY,
    id_producto INTEGER NOT NULL UNIQUE,
    id_servicio INTEGER,
    servicio VARCHAR(255),
    producto_nombre VARCHAR(255),
    precio NUMERIC(10, 2),
    legend TEXT,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);
