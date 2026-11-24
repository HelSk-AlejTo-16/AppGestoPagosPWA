ALTER TABLE direccion
    DROP CONSTRAINT IF EXISTS chk_cp_valido,
    ALTER COLUMN estado TYPE VARCHAR(100),
    ALTER COLUMN pais TYPE VARCHAR(100),
    ALTER COLUMN codigo_postal TYPE VARCHAR(12),
    ADD CONSTRAINT chk_cp_valido CHECK (codigo_postal ~ '^[A-Za-z0-9][A-Za-z0-9 -]{1,10}[A-Za-z0-9]$');
