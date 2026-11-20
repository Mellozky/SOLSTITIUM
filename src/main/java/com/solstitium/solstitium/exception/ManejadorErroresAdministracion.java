package com.solstitium.solstitium.exception;

import com.solstitium.solstitium.controller.BitacoraController;
import com.solstitium.solstitium.controller.ParametroController;
import com.solstitium.solstitium.controller.PanelController;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = {ParametroController.class, BitacoraController.class, PanelController.class})
public class ManejadorErroresAdministracion {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorAdministrativo> entradaInvalida(Exception excepcion) {
        if (excepcion instanceof MethodArgumentNotValidException validacion
                && validacion.getBindingResult().getTarget() instanceof com.solstitium.solstitium.model.dto.SolicitudPanel) {
            return respuesta(HttpStatus.BAD_REQUEST, "Revise los datos del panel.",
                    validacion.getBindingResult().getFieldErrors().stream()
                            .map(error -> error.getField() + ": " + error.getDefaultMessage()).sorted().toList());
        }
        String detalle = excepcion instanceof IllegalArgumentException ? excepcion.getMessage()
                : "Compruebe el formato JSON, los campos permitidos y los tipos solicitados.";
        return respuesta(HttpStatus.BAD_REQUEST, "Revise los cambios administrativos.", List.of(detalle));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorAdministrativo> accesoDenegado() {
        return respuesta(HttpStatus.FORBIDDEN, "Se requiere una cuenta administrativa activa.", List.of());
    }

    @ExceptionHandler(ConflictoCatalogoException.class)
    public ResponseEntity<ErrorAdministrativo> conflictoCatalogo(ConflictoCatalogoException excepcion) {
        return respuesta(HttpStatus.CONFLICT, excepcion.getMessage(), List.of());
    }

    @ExceptionHandler(PanelNoEncontradoException.class)
    public ResponseEntity<ErrorAdministrativo> panelNoEncontrado(PanelNoEncontradoException excepcion) {
        return respuesta(HttpStatus.NOT_FOUND, excepcion.getMessage(), List.of());
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorAdministrativo> metodoNoPermitido() {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "El método solicitado no está permitido.", List.of());
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorAdministrativo> formatoNoPermitido() {
        return respuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Debe enviar Content-Type application/json.", List.of());
    }

    @ExceptionHandler({DataAccessException.class, IllegalStateException.class})
    public ResponseEntity<ErrorAdministrativo> servicioNoDisponible(Exception excepcion) {
        log.error("No fue posible completar la operación administrativa.", excepcion);
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible completar la operación. Intente nuevamente.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorAdministrativo> falloInesperado(Exception excepcion) {
        log.error("Fallo inesperado en administración.", excepcion);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "No fue posible completar la operación administrativa.", List.of());
    }

    private ResponseEntity<ErrorAdministrativo> respuesta(HttpStatus estado, String mensaje, List<String> errores) {
        return ResponseEntity.status(estado).body(new ErrorAdministrativo(mensaje, errores));
    }

    public record ErrorAdministrativo(String mensaje, List<String> errores) {}
}
