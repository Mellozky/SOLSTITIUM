package com.solstitium.solstitium.model.dto;

import java.math.BigDecimal;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Evita coerciones de cadenas, números y booleanos en el contrato administrativo. */
public final class DeserializadoresCatalogo {
    private DeserializadoresCatalogo() {}

    public static class Numero extends ValueDeserializer<BigDecimal> {
        @Override
        public BigDecimal deserialize(JsonParser lector, DeserializationContext contexto) {
            if (!lector.currentToken().isNumeric()) throw new IllegalArgumentException("Debe enviar un número JSON.");
            return lector.getDecimalValue();
        }
    }

    public static class Texto extends ValueDeserializer<String> {
        @Override
        public String deserialize(JsonParser lector, DeserializationContext contexto) {
            if (lector.currentToken() != JsonToken.VALUE_STRING) throw new IllegalArgumentException("Debe enviar un texto JSON.");
            return lector.getString();
        }
    }

    public static class Booleano extends ValueDeserializer<Boolean> {
        @Override
        public Boolean deserialize(JsonParser lector, DeserializationContext contexto) {
            return switch (lector.currentToken()) {
                case VALUE_TRUE -> true;
                case VALUE_FALSE -> false;
                default -> throw new IllegalArgumentException("Debe enviar un booleano JSON.");
            };
        }
    }
}
