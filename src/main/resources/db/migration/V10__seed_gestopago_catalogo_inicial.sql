-- V10__seed_gestopago_catalogo_inicial.sql
-- Inserción de productos y token esencial para garantizar operatividad inmediata en entornos como Render

INSERT INTO gestopago_productos (id_producto, id_servicio, servicio, producto_nombre, precio, tipo_front, tipo_referencia, has_digito_verificador, activo)
VALUES 
    (406, 133, 'Telcel', 'Recarga $100', 100.00, 1, 'a', false, true),
    (405, 133, 'Telcel', 'Recarga $50', 50.00, 1, 'a', false, true),
    (408, 133, 'Telcel', 'Recarga $200', 200.00, 1, 'a', false, true),
    (381, 124, 'Movistar', 'Recarga $100', 100.00, 1, 'a', false, true),
    (377, 124, 'Movistar', 'Recarga $50', 50.00, 1, 'a', false, true),
    (597, 166, 'CFE', 'CFE', 10.00, 2, 'c', false, true),
    (7357, 166, 'CFE', 'Consulta de Saldo', 0.00, 4, 'c', false, true)
ON CONFLICT (id_producto) DO UPDATE SET
    tipo_front = EXCLUDED.tipo_front,
    tipo_referencia = EXCLUDED.tipo_referencia,
    has_digito_verificador = EXCLUDED.has_digito_verificador,
    activo = EXCLUDED.activo;

-- Token inicial para el dispositivo distribuidor
INSERT INTO gestopago_tokens (id_distribuidor, codigo_dispositivo, token, activo, fecha_creacion)
VALUES (83, 'GPS83-TPV-17', 'qmzAAYFmqQbIT/Ktxme8FDV343iYQPwVJM4T39cEinWq1yjc1pp9oxSo4Rjmo5Fk27YzkCDaCr5RM4xtGZNCBZk1MH1tMNbykJ/WzfVjxSEXc9FERnZJPsVuFcAgfsfHmEYEfsLSmLZgovuGmnpDA==', true, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

