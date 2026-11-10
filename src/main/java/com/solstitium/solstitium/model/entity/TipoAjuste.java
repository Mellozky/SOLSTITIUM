package com.solstitium.solstitium.model.entity;

/**
 * Tipo de ajuste que se aplica a la tarifa según el estrato.
 * Corresponde al tipo enumerado {@code tipo_ajuste_estrato} de PostgreSQL.
 */
public enum TipoAjuste {

    /** Estratos 1 a 3: descuento sobre el consumo de subsistencia. */
    SUBSIDIO,

    /** Estrato 4: paga el costo unitario. */
    NINGUNO,

    /** Estratos 5 y 6: recargo sobre todo el consumo. */
    CONTRIBUCION
}
