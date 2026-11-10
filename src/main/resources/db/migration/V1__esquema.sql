-- =====================================================================================
-- SOLSTITIUM · V1 · Esquema inicial
-- Modelo relacional de la base de datos v1.4 (ERS, sección 8.4, figura 3).
--
-- La base guarda solo los parámetros de referencia, el catálogo de paneles, los
-- administradores y la bitácora. Las simulaciones NO se almacenan: no existe ninguna
-- tabla de simulaciones ni de usuarios residenciales.
--
-- Esta migración crea el tipo enumerado, las tablas, sus llaves primarias y foráneas.
-- Las restricciones de unicidad, de dominio y los índices van en V1_1.
-- Una migración ya publicada no se edita: los cambios se hacen en una migración nueva.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Tipos
-- -------------------------------------------------------------------------------------

-- subsidio (estratos 1 a 3), ninguno (estrato 4), contribucion (estratos 5 y 6)
CREATE TYPE tipo_ajuste_estrato AS ENUM ('subsidio', 'ninguno', 'contribucion');

-- -------------------------------------------------------------------------------------
-- Seguridad y auditoría
-- -------------------------------------------------------------------------------------

-- Los administradores se cargan por script; ningún caso de uso los crea.
CREATE TABLE administrador (
    id              serial       NOT NULL,
    usuario         varchar(50)  NOT NULL,
    contrasena_hash varchar(100) NOT NULL,              -- solo el hash BCrypt, nunca la contraseña
    nombre          varchar(100) NOT NULL,
    activo          boolean      NOT NULL DEFAULT true,
    fecha_creacion  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_administrador PRIMARY KEY (id)
);

-- Registra cada valor cambiado por tabla, campo y registro. Por eso no tiene llaves
-- foráneas hacia las tablas de parámetros.
CREATE TABLE bitacora_parametro (
    id               bigserial   NOT NULL,
    administrador_id integer     NOT NULL,
    fecha            timestamptz NOT NULL DEFAULT now(),
    tabla_afectada   varchar(50) NOT NULL,
    registro_id      varchar(50) NOT NULL,
    campo            varchar(50) NOT NULL,
    valor_anterior   text        NOT NULL,
    valor_nuevo      text        NOT NULL,
    CONSTRAINT pk_bitacora_parametro PRIMARY KEY (id),
    CONSTRAINT fk_bitacora_parametro_administrador
        FOREIGN KEY (administrador_id) REFERENCES administrador (id)
);

-- -------------------------------------------------------------------------------------
-- Parámetros generales
-- -------------------------------------------------------------------------------------

-- Parámetros escalares en formato clave-valor (HSP, costo unitario, vida útil, ...).
CREATE TABLE parametro (
    clave               varchar(50)   NOT NULL,
    valor               numeric(14,4) NOT NULL,
    unidad              varchar(20)   NOT NULL,
    descripcion         varchar(200)  NOT NULL,
    fecha_actualizacion timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_parametro PRIMARY KEY (clave)
);

-- Mínimo y máximo permitido de cada parámetro. Se carga por script y el módulo de
-- administración solo lo consulta. "clave" es nula cuando el límite aplica a toda la columna.
CREATE TABLE limite_parametro (
    id           serial        NOT NULL,
    tabla        varchar(50)   NOT NULL,
    campo        varchar(50)   NOT NULL,
    clave        varchar(50),
    valor_minimo numeric(14,4) NOT NULL,
    valor_maximo numeric(14,4) NOT NULL,
    CONSTRAINT pk_limite_parametro PRIMARY KEY (id)
);

-- -------------------------------------------------------------------------------------
-- Tarifa
-- -------------------------------------------------------------------------------------

CREATE TABLE estrato (
    numero            smallint            NOT NULL,
    tipo_ajuste       tipo_ajuste_estrato NOT NULL,     -- fijo por estrato; solo se edita el porcentaje
    porcentaje_ajuste numeric(5,2)        NOT NULL,
    CONSTRAINT pk_estrato PRIMARY KEY (numero)
);

-- -------------------------------------------------------------------------------------
-- Modelo de sombreado
-- -------------------------------------------------------------------------------------

CREATE TABLE franja_horaria (
    id          smallint     NOT NULL,
    nombre      varchar(20)  NOT NULL,
    orden       smallint     NOT NULL,
    hora_inicio time         NOT NULL,
    hora_fin    time         NOT NULL,
    peso        numeric(4,3) NOT NULL,
    CONSTRAINT pk_franja_horaria PRIMARY KEY (id)
);

CREATE TABLE porcion_afectada (
    id       smallint     NOT NULL,
    etiqueta varchar(50)  NOT NULL,
    orden    smallint     NOT NULL,
    valor    numeric(4,3) NOT NULL,
    CONSTRAINT pk_porcion_afectada PRIMARY KEY (id)
);

-- Configuraciones de instalación: inversor central y MLPE.
CREATE TABLE configuracion (
    id                       smallint      NOT NULL,
    codigo                   varchar(20)   NOT NULL,
    nombre                   varchar(60)   NOT NULL,
    costo_kwp                numeric(12,2) NOT NULL,
    coeficiente_recuperacion numeric(4,3)  NOT NULL,
    CONSTRAINT pk_configuracion PRIMARY KEY (id)
);

CREATE TABLE tramo_sombreado (
    id                  smallint     NOT NULL,
    nombre              varchar(30)  NOT NULL,
    orden               smallint     NOT NULL,
    factor_minimo       numeric(4,3) NOT NULL,
    factor_maximo       numeric(4,3) NOT NULL,
    muestra_advertencia boolean      NOT NULL DEFAULT false,
    CONSTRAINT pk_tramo_sombreado PRIMARY KEY (id)
);

-- Configuraciones que se evalúan en cada tramo de sombreado (relación muchos a muchos).
CREATE TABLE tramo_configuracion (
    tramo_id         smallint NOT NULL,
    configuracion_id smallint NOT NULL,
    CONSTRAINT pk_tramo_configuracion PRIMARY KEY (tramo_id, configuracion_id),
    CONSTRAINT fk_tramo_configuracion_tramo
        FOREIGN KEY (tramo_id) REFERENCES tramo_sombreado (id),
    CONSTRAINT fk_tramo_configuracion_configuracion
        FOREIGN KEY (configuracion_id) REFERENCES configuracion (id)
);

-- -------------------------------------------------------------------------------------
-- Catálogo de paneles
-- -------------------------------------------------------------------------------------

-- Los paneles no tienen precio y nunca se eliminan: se activan o desactivan.
CREATE TABLE panel (
    id            serial       NOT NULL,
    marca         varchar(60)  NOT NULL,
    modelo        varchar(60)  NOT NULL,
    potencia_wp   numeric(7,2) NOT NULL,
    area_m2       numeric(5,3) NOT NULL,
    es_referencia boolean      NOT NULL DEFAULT false,
    activo        boolean      NOT NULL DEFAULT true,
    CONSTRAINT pk_panel PRIMARY KEY (id)
);

-- -------------------------------------------------------------------------------------
-- Viabilidad
-- -------------------------------------------------------------------------------------

-- Clasificación según los años de recuperación. "anios_maximo" es nulo en el último rango.
CREATE TABLE rango_viabilidad (
    id            smallint     NOT NULL,
    clasificacion varchar(30)  NOT NULL,
    orden         smallint     NOT NULL,
    anios_minimo  numeric(5,2) NOT NULL,
    anios_maximo  numeric(5,2),
    CONSTRAINT pk_rango_viabilidad PRIMARY KEY (id)
);
