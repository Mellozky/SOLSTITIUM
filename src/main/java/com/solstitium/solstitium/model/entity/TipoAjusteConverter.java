package com.solstitium.solstitium.model.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Convierte {@link TipoAjuste} al valor del tipo enumerado de PostgreSQL y viceversa.
 * En Java las constantes van en mayúsculas (SUBSIDIO); en la base, en minúsculas (subsidio).
 */
@Converter
public class TipoAjusteConverter implements AttributeConverter<TipoAjuste, String> {

    @Override
    public String convertToDatabaseColumn(TipoAjuste tipoAjuste) {
        return tipoAjuste == null ? null : tipoAjuste.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public TipoAjuste convertToEntityAttribute(String valor) {
        return valor == null ? null : TipoAjuste.valueOf(valor.toUpperCase(Locale.ROOT));
    }
}
