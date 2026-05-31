-- Agrega el flag de Presidenta del club al socio.
-- Solo puede haber una presidenta activa a la vez (invariante aplicada en SocioService).
ALTER TABLE socios ADD COLUMN es_presidenta boolean DEFAULT false NOT NULL;

CREATE INDEX idx_socios_presidenta ON socios (es_presidenta) WHERE es_presidenta = true;
