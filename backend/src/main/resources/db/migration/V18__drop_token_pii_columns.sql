-- G-07: eliminar PII pre-cargada del token de verificación de email.
-- nombre, apellido, tipo_socio_nombre y nivel_tecnico_nombre ya no se almacenan
-- en email_verification_tokens. Los socios importados por CSV se crean directamente
-- en la tabla socios durante el confirm; el token solo lleva socio_id.

ALTER TABLE email_verification_tokens
    DROP COLUMN IF EXISTS nombre,
    DROP COLUMN IF EXISTS apellido,
    DROP COLUMN IF EXISTS tipo_socio_nombre,
    DROP COLUMN IF EXISTS nivel_tecnico_nombre;
