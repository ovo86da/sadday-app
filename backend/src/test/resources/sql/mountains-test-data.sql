-- =============================================================================
-- Datos de prueba para el módulo Montañas y Rutas
-- Depende de: socios-test-data.sql (socios con IDs 00000000-0000-4000-b000-000000000001..003, 010)
-- IDs altos (1000+) para no colisionar con el seed de V8/V26.
-- V33: columnas alpinas movidas a rutas_alpinismo. mountain_id nullable en rutas.
-- =============================================================================

-- Montañas de prueba
-- pais añadido en V26 con DEFAULT 'Ecuador', no necesita especificarse
INSERT INTO mountains (id, nombre, region, altitud, created_at, updated_at) VALUES
(1000, 'Monte Test',   'Sierra Test', 4500, NOW(), NOW()),
(1001, 'Monte Target', 'Andes Test',  5000, NOW(), NOW());

-- Rutas de prueba
-- Después de V33 los campos técnicos de alpinismo van en rutas_alpinismo.

-- Ruta pendiente de aprobación, propuesta por Admin
INSERT INTO rutas (id, nombre, tipo_actividad, mountain_id,
                   requiere_permisos, aprobada, propuesta_por_id,
                   created_at, updated_at)
VALUES (2000, 'Ruta Normal', 'ALPINISMO', 1000,
        false, false, '00000000-0000-4000-b000-000000000001',
        NOW(), NOW());

INSERT INTO rutas_alpinismo (ruta_id,
                             escala_alpina_ifas_id, dificultad_roca_id, dificultad_hielo_id,
                             compromiso_id, yosemite_id, sadday_nivel_tecnico_id, sadday_nivel_fisico_id)
VALUES (2000,
        'IFAS001', 'UIAA-F001', 'WI001',
        'C001', 'Y002', 'SA001', 'SA001');

-- Ruta ya aprobada por Admin
INSERT INTO rutas (id, nombre, tipo_actividad, mountain_id,
                   requiere_permisos, aprobada, aprobada_por_id, aprobada_en,
                   propuesta_por_id, created_at, updated_at)
VALUES (2001, 'Ruta Aprobada', 'ALPINISMO', 1000,
        false, true,
        '00000000-0000-4000-b000-000000000001', NOW(),
        '00000000-0000-4000-b000-000000000001',
        NOW(), NOW());

INSERT INTO rutas_alpinismo (ruta_id,
                             escala_alpina_ifas_id, dificultad_roca_id, dificultad_hielo_id,
                             compromiso_id, yosemite_id, sadday_nivel_tecnico_id, sadday_nivel_fisico_id)
VALUES (2001,
        'IFAS002', 'UIAA-F002', 'WI001',
        'C002', 'Y003', 'SA002', 'SA002');
