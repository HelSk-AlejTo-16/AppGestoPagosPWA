CREATE TABLE IF NOT EXISTS gestopago_service_logos (
    id_servicio INTEGER PRIMARY KEY,
    nombre_servicio VARCHAR(150) NOT NULL,
    content_type VARCHAR(100) NOT NULL DEFAULT 'image/png',
    logo_data BYTEA NOT NULL,
    fuente VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
