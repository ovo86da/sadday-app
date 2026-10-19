-- FR-022: archivos GPX, PDF de mapas y enlaces de apps en las rutas
--
-- Tres cosas: un tipo para poder distinguir los documentos de una ruta, una
-- tabla de enlaces con nombre que sustituye a los dos campos de uno en uno, y
-- un contador de confirmaciones por documento.
--
-- Las columnas `rutas.track_url` y `rutas.documentacion_url` se COPIAN, no se
-- borran. Una migración de Flyway ya aplicada no se puede modificar, así que si
-- la copia saliera mal el original tiene que seguir ahí para corregirlo. Se
-- eliminan en una migración posterior, igual que V16, V17 y V18.

-- ───────────────────────────────────────────────────────────────────────────
-- 1. Tipo de documento
-- ───────────────────────────────────────────────────────────────────────────
-- VARCHAR + @Enumerated(STRING), como `rutas.estado` en V7, y no un ENUM
-- nativo: añadir un tipo nuevo no exige entonces tocar la base.
--
-- Las filas existentes pasan a PERMISO porque es lo que son: el servicio las
-- registraba como «Documento de permiso subido».
ALTER TABLE ruta_documentos
    ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'PERMISO';

ALTER TABLE ruta_documentos
    ADD CONSTRAINT ruta_documentos_tipo_check
    CHECK (tipo IN ('PERMISO', 'MAPA', 'TRACK_GPX', 'OTRO'));

COMMENT ON COLUMN ruta_documentos.tipo IS
    'PERMISO = plantilla oficial en blanco · MAPA = PDF del mapa · TRACK_GPX = track · OTRO';

-- ───────────────────────────────────────────────────────────────────────────
-- 2. Enlaces de la ruta
-- ───────────────────────────────────────────────────────────────────────────
-- La etiqueta es texto libre y no un catálogo: las apps de navegación
-- aparecen y desaparecen, y un catálogo obligaría a migrar cada vez que el
-- club empiece a usar una nueva.
CREATE TABLE ruta_enlace (
    id       BIGSERIAL PRIMARY KEY,
    ruta_id  INT          NOT NULL REFERENCES rutas(id) ON DELETE CASCADE,
    etiqueta VARCHAR(100) NOT NULL,
    url      TEXT         NOT NULL,
    orden    SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_ruta_enlace_ruta ON ruta_enlace (ruta_id, orden);

COMMENT ON TABLE ruta_enlace IS
    'Enlaces externos de una ruta: Wikiloc, GaiaGPS, Komoot, fichas de parque...';

-- Copia de los dos campos antiguos. Solo las filas con valor, y descartando
-- las que solo contienen espacios.
INSERT INTO ruta_enlace (ruta_id, etiqueta, url, orden)
SELECT id, 'Track', btrim(track_url), 1
  FROM rutas
 WHERE track_url IS NOT NULL AND btrim(track_url) <> '';

INSERT INTO ruta_enlace (ruta_id, etiqueta, url, orden)
SELECT id, 'Documentación', btrim(documentacion_url), 2
  FROM rutas
 WHERE documentacion_url IS NOT NULL AND btrim(documentacion_url) <> '';

-- ───────────────────────────────────────────────────────────────────────────
-- 3. Confirmaciones de un documento
-- ───────────────────────────────────────────────────────────────────────────
-- La clave primaria compuesta es lo que impide que un socio confirme dos veces
-- el mismo documento, y sirve además de índice para contar.
CREATE TABLE ruta_documento_confirmacion (
    ruta_documento_id UUID      NOT NULL REFERENCES ruta_documentos(id) ON DELETE CASCADE,
    socio_id          UUID      NOT NULL REFERENCES socios(id)         ON DELETE CASCADE,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (ruta_documento_id, socio_id)
);

COMMENT ON TABLE ruta_documento_confirmacion IS
    'Socios que avalan un documento de ruta. Reemplazar el fichero borra sus confirmaciones por el ON DELETE CASCADE.';

-- ───────────────────────────────────────────────────────────────────────────
-- 4. Apps recomendadas al descargar un track
-- ───────────────────────────────────────────────────────────────────────────
-- En configuración y no compilada: así se cambia desde la pantalla de
-- administración sin desplegar nada, ni backend ni frontend ni app móvil.
INSERT INTO configuracion_sistema (clave, valor, descripcion, updated_at)
VALUES ('APPS_RECOMENDADAS_GPX',
        'GaiaGPS, CalTopo, Komoot, Wikiloc',
        'Apps sugeridas al descargar un track GPX, separadas por comas',
        now());
