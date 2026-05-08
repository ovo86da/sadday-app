-- =============================================================================
-- Datos de prueba para el módulo Actas de Reunión
-- Depende de: socios-test-data.sql, salidas-test-data.sql, informes-test-data.sql
-- (informe eeeeeeee-eeee-4eee-beee-eeeeeeeeeeee viene de informes-test-data.sql)
-- =============================================================================

-- Acta de reunión (creada por Admin, tipo SOCIOS para que todos los roles puedan verla)
INSERT INTO actas_reunion (id, fecha, hora, lugar,
                            actividades_realizadas_desc, actividades_por_realizar,
                            varios, observaciones,
                            tipo_acta,
                            creada_por_id, created_at, updated_at)
VALUES ('ffffffff-ffff-4fff-bfff-ffffffffffff',
        '2026-02-01', '19:00:00', 'Sede del club Sadday',
        'Revisión del calendario de salidas del mes de febrero',
        'Planificación de la salida al Chimborazo',
        'Organización del aniversario del club',
        'Sin observaciones adicionales',
        'SOCIOS',
        '00000000-0000-4000-b000-000000000001',
        NOW(), NOW());

-- Asistente: Admin
INSERT INTO asistentes_reunion (acta_id, socio_id)
VALUES ('ffffffff-ffff-4fff-bfff-ffffffffffff',
        '00000000-0000-4000-b000-000000000001');

-- Asistente: Directivo
INSERT INTO asistentes_reunion (acta_id, socio_id)
VALUES ('ffffffff-ffff-4fff-bfff-ffffffffffff',
        '00000000-0000-4000-b000-000000000002');

-- Informe vinculado al acta (informe de la salida REALIZADA)
INSERT INTO acta_informes_salida (acta_id, informe_id)
VALUES ('ffffffff-ffff-4fff-bfff-ffffffffffff',
        'eeeeeeee-eeee-4eee-beee-eeeeeeeeeeee');
