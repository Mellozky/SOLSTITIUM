package com.solstitium.solstitium.model.dto;

import java.util.ArrayList;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Conserva exactamente los decimales JSON, sin pasar por double. */
public class DeserializadorValorParametro extends ValueDeserializer<Object> {
    @Override
    public Object deserialize(JsonParser lector, DeserializationContext contexto) {
        return switch (lector.currentToken()) {
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> lector.getDecimalValue();
            case VALUE_STRING -> lector.getString();
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_NULL -> null;
            case START_ARRAY -> {
                var valores = new ArrayList<Object>();
                while (lector.nextToken() != JsonToken.END_ARRAY) {
                    if (lector.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                        throw new IllegalArgumentException("Las configuraciones deben ser identificadores enteros.");
                    }
                    valores.add(lector.getDecimalValue());
                }
                yield valores;
            }
            default -> throw new IllegalArgumentException("El valor tiene un tipo no permitido.");
        };
    }
}
