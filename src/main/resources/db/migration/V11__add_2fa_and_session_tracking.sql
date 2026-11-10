-- Migración V11: Soporte para Autenticación de Doble Factor (2FA) y Control de Sesión Única Concurrente (Web / Móvil)

ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS codigo_2fa VARCHAR(100),
    ADD COLUMN IF NOT EXISTS expiracion_2fa TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS id_sesion_activa VARCHAR(100),
    ADD COLUMN IF NOT EXISTS plataforma_activa VARCHAR(20),
    ADD COLUMN IF NOT EXISTS fecha_inicio_sesion TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_usuarios_sesion_activa ON usuarios(id_sesion_activa);
