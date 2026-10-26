-- Actualizar gestopago_productos con campos de validación tipoFront
ALTER TABLE gestopago_productos ADD COLUMN IF NOT EXISTS tipo_front INTEGER;
ALTER TABLE gestopago_productos ADD COLUMN IF NOT EXISTS tipo_referencia VARCHAR(20);
ALTER TABLE gestopago_productos ADD COLUMN IF NOT EXISTS has_digito_verificador BOOLEAN DEFAULT FALSE;

-- Enum de estados para el flujo de reserva y compensación de pago de servicios
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'estado_pago_servicio_enum') THEN
        CREATE TYPE estado_pago_servicio_enum AS ENUM (
            'PENDIENTE',
            'EXITOSO',
            'RECHAZADO',
            'PENDIENTE_CONCILIACION',
            'REVERTIDO'
        );
    END IF;
END$$;

-- Tabla de persistencia y auditoría de pagos de servicios
CREATE TABLE IF NOT EXISTS pagos_servicios (
    id_pago UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cuenta_id UUID NOT NULL REFERENCES cuenta_bancaria(id_bancaria) ON DELETE RESTRICT,
    id_servicio VARCHAR(30) NOT NULL,
    id_producto VARCHAR(30) NOT NULL,
    nombre_servicio VARCHAR(150),
    nombre_producto VARCHAR(150),
    referencia VARCHAR(100) NOT NULL,
    monto DECIMAL(15, 2) NOT NULL,
    upc VARCHAR(10) NOT NULL UNIQUE,
    estado estado_pago_servicio_enum NOT NULL DEFAULT 'PENDIENTE',
    codigo_respuesta_proveedor VARCHAR(20),
    mensaje_proveedor TEXT,
    folio_autorizacion VARCHAR(100),
    folio_transaccion_gp VARCHAR(100),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_monto_pago_positivo CHECK (monto > 0.00)
);

CREATE INDEX IF NOT EXISTS idx_pagos_servicios_cuenta ON pagos_servicios(cuenta_id);
CREATE INDEX IF NOT EXISTS idx_pagos_servicios_upc ON pagos_servicios(upc);
CREATE INDEX IF NOT EXISTS idx_pagos_servicios_estado ON pagos_servicios(estado);
CREATE INDEX IF NOT EXISTS idx_pagos_servicios_creado_en ON pagos_servicios(creado_en DESC);

