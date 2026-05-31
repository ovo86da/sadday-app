-- Reemplaza la columna boolean `aprobada` por un enum de 3 estados.

ALTER TABLE rutas ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';
UPDATE rutas SET estado = 'APROBADA' WHERE aprobada = true;

ALTER TABLE rutas ADD COLUMN revisada_por_id UUID REFERENCES socios(id);
UPDATE rutas SET revisada_por_id = aprobada_por_id;

ALTER TABLE rutas ADD COLUMN revisada_en TIMESTAMP;
UPDATE rutas SET revisada_en = aprobada_en;

ALTER TABLE rutas ADD COLUMN motivo_rechazo TEXT;

ALTER TABLE rutas DROP COLUMN aprobada;
ALTER TABLE rutas DROP COLUMN aprobada_por_id;
ALTER TABLE rutas DROP COLUMN aprobada_en;
