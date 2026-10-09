DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM personas
        GROUP BY upper(btrim(curp))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'No se puede crear la unicidad de CURP: existen duplicados ignorando mayúsculas o espacios.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM personas
        GROUP BY upper(btrim(rfc))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'No se puede crear la unicidad de RFC: existen duplicados ignorando mayúsculas o espacios.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM usuarios
        GROUP BY lower(btrim(correo))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'No se puede crear la unicidad de correo: existen duplicados ignorando mayúsculas o espacios.';
    END IF;
END;
$$;

CREATE UNIQUE INDEX ux_personas_curp_normalizada ON personas (upper(btrim(curp)));
CREATE UNIQUE INDEX ux_personas_rfc_normalizado ON personas (upper(btrim(rfc)));
CREATE UNIQUE INDEX ux_usuarios_correo_normalizado ON usuarios (lower(btrim(correo)));

CREATE OR REPLACE FUNCTION es_clabe_valida(valor TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
IMMUTABLE
STRICT
AS $$
DECLARE
    suma INTEGER := 0;
    posicion INTEGER;
    digito INTEGER;
    peso INTEGER;
BEGIN
    IF valor !~ '^[0-9]{18}$' THEN
        RETURN FALSE;
    END IF;

    FOR posicion IN 1..17 LOOP
        peso := CASE (posicion - 1) % 3
            WHEN 0 THEN 3
            WHEN 1 THEN 7
            ELSE 1
        END;
        digito := substring(valor FROM posicion FOR 1)::INTEGER;
        suma := suma + (digito * peso) % 10;
    END LOOP;

    RETURN substring(valor FROM 18 FOR 1)::INTEGER = (10 - suma % 10) % 10;
END;
$$;

ALTER TABLE personas
    ADD CONSTRAINT chk_curp_formato
        CHECK (upper(btrim(curp)) ~
               '^[A-Z][AEIOUX][A-Z]{2}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[HM](AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]$'),
    ADD CONSTRAINT chk_rfc_formato
        CHECK (upper(btrim(rfc)) ~ '^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$');

ALTER TABLE cuenta_bancaria
    ADD CONSTRAINT chk_clabe_formato
        CHECK (es_clabe_valida(clabe));
