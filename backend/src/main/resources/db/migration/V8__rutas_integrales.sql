-- FR-020: Soporte para rutas de tipo INTEGRAL (multi-cumbre)

CREATE TABLE rutas_integral (
    ruta_id                       INT          NOT NULL PRIMARY KEY REFERENCES rutas(id) ON DELETE CASCADE,
    dificultad_maxima_descripcion VARCHAR(200),
    descripcion_itinerario        TEXT
);

CREATE TABLE ruta_cumbres (
    ruta_id     INT      NOT NULL REFERENCES rutas(id) ON DELETE CASCADE,
    mountain_id INT      NOT NULL REFERENCES mountains(id),
    secuencia   SMALLINT NOT NULL,
    PRIMARY KEY (ruta_id, secuencia),
    UNIQUE (ruta_id, mountain_id)
);
