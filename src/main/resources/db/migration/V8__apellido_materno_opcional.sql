-- El apellido materno es opcional en la entidad Cliente y en los DTO de registro/actualización,
-- pero la tabla lo declaraba NOT NULL (V4), lo que hacía fallar cualquier alta sin apellido materno.
ALTER TABLE personas ALTER COLUMN apellido_materno DROP NOT NULL;

