package com.solstitium.solstitium.exception;

/** Datos rechazados al crear un administrador o restablecer su contraseña por script. */
public class DatosAdministradorInvalidosException extends RuntimeException {
    public DatosAdministradorInvalidosException(String mensaje) { super(mensaje); }
}
