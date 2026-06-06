-- =============================================================================
-- Datos de prueba para tests del módulo Contactos de Emergencia.
-- Los socios base se cargan desde socios-test-data.sql.
-- =============================================================================

-- Contactos existentes para el socio Admin (ID 000000000001)
INSERT INTO socio_emergency_contacts (id, socio_id, orden, nombre_completo, relacion, celular, direccion)
VALUES (
    'eeeeeeee-eeee-4eee-beee-eeeeeeeeee01',
    '00000000-0000-4000-b000-000000000001',
    1,
    'Contacto Uno Admin', 'Familiar', '0991234567', 'Quito, Ecuador'
);

INSERT INTO socio_emergency_contacts (id, socio_id, orden, nombre_completo, relacion, celular, direccion)
VALUES (
    'eeeeeeee-eeee-4eee-beee-eeeeeeeeee02',
    '00000000-0000-4000-b000-000000000001',
    2,
    'Contacto Dos Admin', 'Amigo', '0997654321', 'Quito, Ecuador'
);
