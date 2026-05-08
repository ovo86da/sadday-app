-- =============================================================================
-- Datos de prueba para el módulo Salidas
-- Depende de: socios-test-data.sql (socios con IDs 00000000-0000-4000-b000-000000000001..003, 010)
-- V33: columnas alpinas movidas a rutas_alpinismo; mountain_id nullable.
-- V32: mountain_id=1 (Chimborazo Andes) fue eliminado; usar mountain_id=13 (V26).
-- =============================================================================

-- Ruta aprobada para usar en las salidas de prueba
-- Después de V33 los campos técnicos de alpinismo van en rutas_alpinismo.
INSERT INTO rutas (id, nombre, tipo_actividad, mountain_id,
                   requiere_permisos, aprobada, aprobada_por_id, aprobada_en,
                   propuesta_por_id, created_at, updated_at)
VALUES (5000, 'Ruta Test Salidas', 'ALPINISMO', 13,
        false, true,
        '00000000-0000-4000-b000-000000000001', NOW(),
        '00000000-0000-4000-b000-000000000001',
        NOW(), NOW());

INSERT INTO rutas_alpinismo (ruta_id,
                             escala_alpina_ifas_id, dificultad_roca_id, dificultad_hielo_id,
                             compromiso_id, yosemite_id, sadday_nivel_tecnico_id, sadday_nivel_fisico_id)
VALUES (5000,
        'IFAS002', 'UIAA-F002', 'WI001',
        'C002', 'Y003', 'SA002', 'SA002');

-- Salida PLANIFICADA (capacidad 5)
INSERT INTO salida (id, nombre, fecha_inicio, hora_encuentro_club, fecha_fin,
                    ruta_id, tipo_actividad, publico_objetivo_id, formato_salida_id,
                    nivel_minimo_requerido_id,
                    capacidad_maxima, estado, creado_por_id, created_at, updated_at)
VALUES ('aaaaaaaa-aaaa-4aaa-baaa-aaaaaaaaaaaa',
        'Salida Test Planificada',
        '2026-06-01', '05:00:00', '2026-06-03',
        5000, 'ALPINISMO', 'PO001', 'FS001', 'SO002',
        5, 'PLANIFICADA',
        '00000000-0000-4000-b000-000000000001',
        NOW(), NOW());

-- Salida PLANIFICADA con capacidad 1 (para probar error "salida llena")
INSERT INTO salida (id, nombre, fecha_inicio, hora_encuentro_club, fecha_fin,
                    ruta_id, tipo_actividad, publico_objetivo_id, formato_salida_id,
                    capacidad_maxima, estado, creado_por_id, created_at, updated_at)
VALUES ('bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb',
        'Salida Llena',
        '2026-07-01', '06:00:00', '2026-07-03',
        5000, 'ALPINISMO', 'PO001', 'FS001',
        1, 'PLANIFICADA',
        '00000000-0000-4000-b000-000000000001',
        NOW(), NOW());

-- Admin ya inscrito en la salida planificada (para tests: duplicado, jefe, dignidades)
INSERT INTO salida_participantes (salida_id, socio_id, estado_inscripcion, created_at, updated_at)
VALUES ('aaaaaaaa-aaaa-4aaa-baaa-aaaaaaaaaaaa',
        '00000000-0000-4000-b000-000000000001',
        'INSCRITO', NOW(), NOW());

-- Admin ya inscrito en la salida llena (la llena)
INSERT INTO salida_participantes (salida_id, socio_id, estado_inscripcion, created_at, updated_at)
VALUES ('bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb',
        '00000000-0000-4000-b000-000000000001',
        'INSCRITO', NOW(), NOW());
