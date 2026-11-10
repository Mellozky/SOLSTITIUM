-- =====================================================================================
-- SOLSTITIUM · V1.1 · Restricciones e índices
-- Completa el esquema de V1 con lo que indica el modelo relacional v1.4 (ERS, figura 3):
-- columnas únicas, índice único (marca, modelo), un solo panel de referencia e índice
-- único (tabla, campo, clave) en limite_parametro.
--
-- Además agrega restricciones de dominio básicas. Son la última barrera ante datos
-- imposibles; los rangos de negocio configurables se validan en la aplicación contra
-- limite_parametro, no aquí.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Unicidad (figura 3: columnas marcadas "único")
-- -------------------------------------------------------------------------------------

ALTER TABLE administrador    ADD CONSTRAINT uq_administrador_usuario          UNIQUE (usuario);
ALTER TABLE configuracion    ADD CONSTRAINT uq_configuracion_codigo           UNIQUE (codigo);
ALTER TABLE tramo_sombreado  ADD CONSTRAINT uq_tramo_sombreado_nombre         UNIQUE (nombre);
ALTER TABLE franja_horaria   ADD CONSTRAINT uq_franja_horaria_nombre          UNIQUE (nombre);
ALTER TABLE porcion_afectada ADD CONSTRAINT uq_porcion_afectada_etiqueta      UNIQUE (etiqueta);
ALTER TABLE rango_viabilidad ADD CONSTRAINT uq_rango_viabilidad_clasificacion UNIQUE (clasificacion);

-- Un panel se identifica por su marca y modelo.
ALTER TABLE panel ADD CONSTRAINT uq_panel_marca_modelo UNIQUE (marca, modelo);

-- Como máximo un panel de referencia: el índice solo incluye las filas con es_referencia = true.
CREATE UNIQUE INDEX uq_panel_referencia ON panel (es_referencia) WHERE es_referencia;

-- Un solo límite por (tabla, campo, clave). "clave" puede ser nula, así que los nulos
-- se tratan como iguales para que tampoco se repita un límite sin clave.
CREATE UNIQUE INDEX uq_limite_parametro_tabla_campo_clave
    ON limite_parametro (tabla, campo, clave) NULLS NOT DISTINCT;

-- -------------------------------------------------------------------------------------
-- Dominio
-- -------------------------------------------------------------------------------------

ALTER TABLE estrato
    ADD CONSTRAINT ck_estrato_numero            CHECK (numero BETWEEN 1 AND 6),
    ADD CONSTRAINT ck_estrato_porcentaje_ajuste CHECK (porcentaje_ajuste BETWEEN 0 AND 100);

ALTER TABLE franja_horaria
    ADD CONSTRAINT ck_franja_horaria_peso  CHECK (peso BETWEEN 0 AND 1),
    ADD CONSTRAINT ck_franja_horaria_horas CHECK (hora_inicio < hora_fin);

ALTER TABLE porcion_afectada
    ADD CONSTRAINT ck_porcion_afectada_valor CHECK (valor BETWEEN 0 AND 1);

ALTER TABLE configuracion
    ADD CONSTRAINT ck_configuracion_costo_kwp   CHECK (costo_kwp > 0),
    ADD CONSTRAINT ck_configuracion_coeficiente CHECK (coeficiente_recuperacion BETWEEN 0 AND 1);

ALTER TABLE tramo_sombreado
    ADD CONSTRAINT ck_tramo_sombreado_factores
        CHECK (factor_minimo >= 0 AND factor_maximo <= 1 AND factor_minimo <= factor_maximo);

ALTER TABLE panel
    ADD CONSTRAINT ck_panel_potencia_wp CHECK (potencia_wp > 0),
    ADD CONSTRAINT ck_panel_area_m2     CHECK (area_m2 > 0),
    -- Un panel inactivo no puede ser el de referencia.
    ADD CONSTRAINT ck_panel_referencia_activa CHECK (NOT es_referencia OR activo);

ALTER TABLE rango_viabilidad
    ADD CONSTRAINT ck_rango_viabilidad_anios
        CHECK (anios_minimo >= 0 AND (anios_maximo IS NULL OR anios_maximo > anios_minimo));

ALTER TABLE limite_parametro
    ADD CONSTRAINT ck_limite_parametro_rango CHECK (valor_minimo <= valor_maximo);

-- -------------------------------------------------------------------------------------
-- Índices de consulta
-- -------------------------------------------------------------------------------------

-- La bitácora se lista con el cambio más reciente primero.
CREATE INDEX ix_bitacora_parametro_fecha ON bitacora_parametro (fecha DESC);

-- PostgreSQL no indexa las llaves foráneas por sí solo.
CREATE INDEX ix_bitacora_parametro_administrador ON bitacora_parametro (administrador_id);
