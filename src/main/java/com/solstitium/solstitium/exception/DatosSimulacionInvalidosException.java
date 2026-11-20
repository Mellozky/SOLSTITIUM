package com.solstitium.solstitium.exception;

import java.util.List;

/** Conserva los errores de entrada para que el futuro manejador HTTP pueda mostrarlos. */
public class DatosSimulacionInvalidosException extends IllegalArgumentException {

    private final List<String> errores;

    public DatosSimulacionInvalidosException(List<String> errores) {
        super(String.join(" ", errores));
        this.errores = List.copyOf(errores);
    }

    public List<String> getErrores() {
        return errores;
    }
}
