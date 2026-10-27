-- V10__seed_gestopago_catalogo_inicial.sql
-- Registro base del dispositivo distribuidor; el catálogo de productos se sincroniza dinámicamente mediante GestoPagoCatalogSyncTask (CRON)
INSERT INTO gestopago_tokens (id_distribuidor, codigo_dispositivo, token, activo, fecha_creacion)
VALUES (83, 'GPS83-TPV-17', '', true, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
