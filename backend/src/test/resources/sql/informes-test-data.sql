-- =============================================================================
-- Datos de prueba para el módulo Informes
-- Depende de: socios-test-data.sql, salidas-test-data.sql (ruta ID 5000)
-- =============================================================================

-- Salida EN_CURSO (para crear informes)
INSERT INTO salida (id, nombre, fecha_inicio, hora_encuentro_club, fecha_fin,
                    ruta_id, tipo_actividad, publico_objetivo_id, formato_salida_id,
                    capacidad_maxima, estado, creado_por_id, created_at, updated_at)
VALUES ('cccccccc-cccc-4ccc-bccc-cccccccccccc',
        'Salida En Curso Test',
        '2026-01-15', '05:00:00', '2026-01-17',
        5000, 'ALPINISMO', 'PO001', 'FS001',
        10, 'EN_CURSO',
        '00000000-0000-4000-b000-000000000001',
        NOW(), NOW());

-- Admin inscrito (será designado Jefe de Salida vía dignidades)
INSERT INTO salida_participantes (salida_id, socio_id, estado_inscripcion, created_at, updated_at)
VALUES ('cccccccc-cccc-4ccc-bccc-cccccccccccc',
        '00000000-0000-4000-b000-000000000001',
        'INSCRITO', NOW(), NOW());

-- Directivo inscrito (para agregar reconocimientos)
INSERT INTO salida_participantes (salida_id, socio_id, estado_inscripcion, created_at, updated_at)
VALUES ('cccccccc-cccc-4ccc-bccc-cccccccccccc',
        '00000000-0000-4000-b000-000000000002',
        'INSCRITO', NOW(), NOW());

-- Asignar dignidad "Jefe de Salida" al Admin en la salida EN_CURSO
INSERT INTO salida_participante_dignidades (participante_id, dignidad_id)
SELECT sp.id, d.id
FROM   salida_participantes sp CROSS JOIN dignidades d
WHERE  sp.salida_id = 'cccccccc-cccc-4ccc-bccc-cccccccccccc'
  AND  sp.socio_id  = '00000000-0000-4000-b000-000000000001'
  AND  d.nombre     = 'Jefe de Salida';

-- Salida REALIZADA con informe ya creado (para test de duplicado y validación)
INSERT INTO salida (id, nombre, fecha_inicio, hora_encuentro_club, fecha_fin,
                    ruta_id, tipo_actividad, publico_objetivo_id, formato_salida_id,
                    capacidad_maxima, estado, creado_por_id, created_at, updated_at)
VALUES ('dddddddd-dddd-4ddd-bddd-dddddddddddd',
        'Salida Realizada Test',
        '2026-01-01', '05:00:00', '2026-01-03',
        5000, 'ALPINISMO', 'PO001', 'FS001',
        10, 'REALIZADA',
        '00000000-0000-4000-b000-000000000001',
        NOW(), NOW());

INSERT INTO salida_participantes (salida_id, socio_id, estado_inscripcion, created_at, updated_at)
VALUES ('dddddddd-dddd-4ddd-bddd-dddddddddddd',
        '00000000-0000-4000-b000-000000000001',
        'INSCRITO', NOW(), NOW());

INSERT INTO salida_participantes (salida_id, socio_id, estado_inscripcion, created_at, updated_at)
VALUES ('dddddddd-dddd-4ddd-bddd-dddddddddddd',
        '00000000-0000-4000-b000-000000000002',
        'INSCRITO', NOW(), NOW());

-- Asignar dignidad "Jefe de Salida" al Admin en la salida REALIZADA
INSERT INTO salida_participante_dignidades (participante_id, dignidad_id)
SELECT sp.id, d.id
FROM   salida_participantes sp CROSS JOIN dignidades d
WHERE  sp.salida_id = 'dddddddd-dddd-4ddd-bddd-dddddddddddd'
  AND  sp.socio_id  = '00000000-0000-4000-b000-000000000001'
  AND  d.nombre     = 'Jefe de Salida';

-- Informe pre-existente para la salida REALIZADA (para test de duplicado y validación)
-- alquilo_transporte fue eliminado en V21 (reemplazado por segmentos_viaje)
INSERT INTO informe_salida (id, salida_id, se_realizo, logro_cumbre, condiciones_meteorologicas,
                             cronica, alquilo_guia, created_at, updated_at)
VALUES ('eeeeeeee-eeee-4eee-beee-eeeeeeeeeeee',
        'dddddddd-dddd-4ddd-bddd-dddddddddddd',
        true, true, 'Soleado',
        'Crónica de prueba', false, NOW(), NOW());
