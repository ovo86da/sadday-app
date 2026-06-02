-- FR-020: añadir INTEGRAL a los CHECK constraints de tipo_actividad

ALTER TABLE rutas
    DROP CONSTRAINT chk_tipo_actividad,
    ADD CONSTRAINT chk_tipo_actividad CHECK (
        tipo_actividad IN ('ALPINISMO', 'ESCALADA', 'TREKKING', 'CICLISMO', 'INTEGRAL')
    );

ALTER TABLE salidas
    DROP CONSTRAINT salida_tipo_actividad_check,
    ADD CONSTRAINT salida_tipo_actividad_check CHECK (
        tipo_actividad IS NULL OR
        tipo_actividad IN ('ALPINISMO', 'ESCALADA', 'TREKKING', 'CICLISMO', 'INTEGRAL')
    );
