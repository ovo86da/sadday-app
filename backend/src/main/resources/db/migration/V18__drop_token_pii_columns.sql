-- G-07: eliminar PII pre-cargada del token de verificación de email.
-- nombre, apellido, tipo_socio_nombre y nivel_tecnico_nombre ya no se almacenan;
-- el socio ingresa todos sus datos personales al completar el registro.
-- Se agrega from_csv_import como boolean para distinguir el flujo de importación CSV.

ALTER TABLE email_verification_tokens
    DROP COLUMN IF EXISTS nombre,
    DROP COLUMN IF EXISTS apellido,
    DROP COLUMN IF EXISTS tipo_socio_nombre,
    DROP COLUMN IF EXISTS nivel_tecnico_nombre,
    ADD COLUMN IF NOT EXISTS from_csv_import BOOLEAN NOT NULL DEFAULT FALSE;
