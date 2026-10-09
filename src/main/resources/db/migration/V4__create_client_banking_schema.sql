DROP TABLE IF EXISTS informacion_laboral CASCADE;
DROP TABLE IF EXISTS direccion CASCADE;
DROP TABLE IF EXISTS contactos CASCADE;
DROP TABLE IF EXISTS cuenta_bancaria CASCADE;
DROP TABLE IF EXISTS usuarios CASCADE;
DROP TABLE IF EXISTS personas CASCADE;
DROP TYPE IF EXISTS sexo_enum CASCADE;
DROP TYPE IF EXISTS estado_civil_enum CASCADE;

CREATE TYPE sexo_enum AS ENUM ('M', 'F', 'O');
CREATE TYPE estado_civil_enum AS ENUM ('Soltero', 'Casado', 'Divorciado', 'Viudo', 'Concubinato');

CREATE TABLE personas (
    id_persona UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_nacimiento DATE NOT NULL,
    sexo sexo_enum NOT NULL,
    estado_civil estado_civil_enum NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    curp CHAR(18) NOT NULL UNIQUE,
    rfc VARCHAR(13) NOT NULL UNIQUE,
    nombre VARCHAR(50) NOT NULL,
    segundo_nombre VARCHAR(50),
    apellido_paterno VARCHAR(50) NOT NULL,
    apellido_materno VARCHAR(50) NOT NULL,
    nacionalidad VARCHAR(50) NOT NULL DEFAULT 'Mexicana',
    CONSTRAINT chk_nacimiento CHECK (fecha_nacimiento <= CURRENT_DATE),
    CONSTRAINT chk_rfc_length CHECK (LENGTH(rfc) IN (12, 13)),
    CONSTRAINT chk_curp_length CHECK (LENGTH(curp) = 18),
    CONSTRAINT chk_mayoria_edad CHECK (fecha_nacimiento <= CURRENT_DATE - INTERVAL '18 years')
);

CREATE TABLE contactos (
    id_contactos UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    persona_id UUID NOT NULL UNIQUE REFERENCES personas(id_persona) ON DELETE CASCADE,
    telefono_movil CHAR(10) NOT NULL,
    telefono_alternativo VARCHAR(10),
    CONSTRAINT chk_tel_movil_valido CHECK (telefono_movil ~ '^[0-9]{10}$'),
    CONSTRAINT chk_tel_movil_alternativo_valido CHECK (
        telefono_alternativo IS NULL OR telefono_alternativo ~ '^[0-9]{10}$'
    )
);

CREATE TABLE direccion (
    id_direccion UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    persona_id UUID NOT NULL UNIQUE REFERENCES personas(id_persona) ON DELETE CASCADE,
    calle VARCHAR(100) NOT NULL,
    numero_exterior VARCHAR(20) NOT NULL,
    numero_interior VARCHAR(20),
    colonia VARCHAR(100) NOT NULL,
    municipio VARCHAR(100) NOT NULL,
    estado VARCHAR(50) NOT NULL,
    codigo_postal VARCHAR(5) NOT NULL,
    pais VARCHAR(50) NOT NULL DEFAULT 'México',
    CONSTRAINT chk_cp_valido CHECK (codigo_postal ~ '^[0-9]{5}$')
);

CREATE TABLE informacion_laboral (
    id_laboral UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    persona_id UUID NOT NULL UNIQUE REFERENCES personas(id_persona) ON DELETE CASCADE,
    ingreso_mensual DECIMAL(12, 2) NOT NULL,
    ocupacion VARCHAR(100) NOT NULL,
    empresa VARCHAR(100) NOT NULL,
    CONSTRAINT chk_ingreso CHECK (ingreso_mensual > 0.00)
);

CREATE TABLE cuenta_bancaria (
    id_bancaria UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    persona_id UUID NOT NULL REFERENCES personas(id_persona) ON DELETE CASCADE,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    clabe VARCHAR(18) NOT NULL UNIQUE,
    numero_cuenta VARCHAR(20) NOT NULL UNIQUE,
    saldo DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_saldo_positivo CHECK (saldo >= 0.00)
);

CREATE TABLE usuarios (
    id_usuario UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    persona_id UUID NOT NULL UNIQUE REFERENCES personas(id_persona) ON DELETE CASCADE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    correo VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

CREATE INDEX idx_personas_activo ON personas(activo);
CREATE INDEX idx_personas_creado_en ON personas(creado_en);
CREATE INDEX idx_cuenta_persona_activa ON cuenta_bancaria(persona_id, activa);
CREATE INDEX idx_usuarios_persona_activo ON usuarios(persona_id, activo);

CREATE OR REPLACE FUNCTION desactivar_relaciones_por_cliente()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.activo = TRUE AND NEW.activo = FALSE THEN
        UPDATE usuarios
        SET activo = FALSE, fecha_actualizacion = CURRENT_TIMESTAMP
        WHERE persona_id = NEW.id_persona;
        UPDATE cuenta_bancaria
        SET activa = FALSE
        WHERE persona_id = NEW.id_persona;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_baja_logica_cliente
AFTER UPDATE OF activo ON personas
FOR EACH ROW
WHEN (OLD.activo = TRUE AND NEW.activo = FALSE)
EXECUTE FUNCTION desactivar_relaciones_por_cliente();

CREATE OR REPLACE FUNCTION verificar_cliente_activo()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.activa AND NOT EXISTS (
        SELECT 1 FROM personas WHERE id_persona = NEW.persona_id AND activo = TRUE
    ) THEN
        RAISE EXCEPTION 'No se pueden crear cuentas para un cliente inactivo.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_verificar_cliente_activo
BEFORE INSERT OR UPDATE OF activa, persona_id ON cuenta_bancaria
FOR EACH ROW
EXECUTE FUNCTION verificar_cliente_activo();
