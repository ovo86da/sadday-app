-- =============================================================================
-- V4: Nuevos estados de habilitación, tipo de socio y parámetros de config
-- =============================================================================

-- Nuevos estados de habilitación (IDs 4 y 5)
INSERT INTO public.estado_habilitacion (id, nombre, descripcion) VALUES
(4, 'Licencia',       'Socio con permiso de ausencia temporal aprobado por la directiva.'),
(5, 'Re-inscripción', 'Socio inactivo prolongado; debe pagar cuota de re-inscripción para reactivarse.');

SELECT pg_catalog.setval('public.estado_habilitacion_id_seq', 5, true);

-- Nuevo tipo de socio (ID 6)
INSERT INTO public.tipo_socio_club (id, nombre, descripcion) VALUES
(6, 'Ausente', 'Socio que se ha ausentado del club por un período prolongado.');

-- tipo_socio_club_id_seq ya está en 6 desde V2 — sin cambio necesario

-- Nuevos parámetros de configuración del sistema
INSERT INTO public.configuracion_sistema (id, clave, valor, descripcion, updated_by_id) VALUES
(4, 'BLOQUEAR_INSCRIPCION_LICENCIA',      'true', 'Si es true, los socios en estado Licencia quedan bloqueados para inscribirse en salidas.', NULL),
(5, 'BLOQUEAR_INSCRIPCION_REINSCRIPCION', 'true', 'Si es true, los socios en estado Re-inscripción quedan bloqueados para inscribirse en salidas.', NULL);

SELECT pg_catalog.setval('public.configuracion_sistema_id_seq', 5, true);
