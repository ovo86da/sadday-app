-- Socios creados vía importación CSV se registran sin fecha de nacimiento;
-- el socio la completa en su perfil después del primer login.

ALTER TABLE socios ALTER COLUMN fecha_nacimiento DROP NOT NULL;
