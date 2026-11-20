package com.solstitium.solstitium.exception;

public class PanelNoEncontradoException extends RuntimeException {
    public PanelNoEncontradoException() { super("El panel solicitado no existe."); }
}
