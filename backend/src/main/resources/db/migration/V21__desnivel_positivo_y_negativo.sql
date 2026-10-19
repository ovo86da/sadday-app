-- =============================================================================
-- V21 — Desnivel positivo y negativo por separado (FR-024)
--
-- `desnivel_m` guardaba un solo numero y en ningun sitio estaba escrito si era
-- la subida, la bajada o la suma. El unico que lo declaraba era el MCP, de
-- pasada, en la descripcion de un parametro.
--
-- Se renombra en vez de añadir al lado porque no hay nada en produccion: las 70
-- rutas las siembra V2__seed_data.sql y no hay usuarios. Con datos reales, un
-- campo que significa "positivo" sin decirlo se queda asi para siempre.
--
-- Las dos columnas guardan MAGNITUDES, siempre >= 0: el negativo de la travesia
-- Mojanda-San Pablo se guarda como 1248, no como -1248. El signo es cosa de la
-- presentacion; guardarlo en la base obligaria a recordar el convenio en cada
-- consulta y a decidir que hacer con un -0.
-- =============================================================================

-- El check anterior era `> 0`, que rechaza el cero. Un desnivel negativo de
-- cero es legitimo: sincholagua-camping sube 1038 m y no baja nada porque
-- termina en el campamento. Si se copiara el `> 0` tal cual, esa ruta no se
-- podria guardar y el error saldria como una violacion de constraint sin
-- explicar nada.
ALTER TABLE public.rutas DROP CONSTRAINT rutas_desnivel_m_check;

ALTER TABLE public.rutas RENAME COLUMN desnivel_m TO desnivel_positivo_m;

ALTER TABLE public.rutas ADD COLUMN desnivel_negativo_m integer;

ALTER TABLE public.rutas
    ADD CONSTRAINT rutas_desnivel_positivo_m_check CHECK (desnivel_positivo_m >= 0);

ALTER TABLE public.rutas
    ADD CONSTRAINT rutas_desnivel_negativo_m_check CHECK (desnivel_negativo_m >= 0);

-- El negativo de las rutas que ya existen queda en NULL a proposito, no en 0.
-- NULL es "no lo sabemos"; 0 seria afirmar que no bajan nada, y eso es
-- inventarse un dato. Se completara cuando alguien suba un track (FR-023) o
-- edite la ruta a mano.

COMMENT ON COLUMN public.rutas.desnivel_positivo_m IS
    'Desnivel acumulado de subida en metros. Magnitud, siempre >= 0.';
COMMENT ON COLUMN public.rutas.desnivel_negativo_m IS
    'Desnivel acumulado de bajada en metros. Magnitud sin signo, siempre >= 0. '
    'NULL significa que no se conoce, no que la ruta no baje.';
