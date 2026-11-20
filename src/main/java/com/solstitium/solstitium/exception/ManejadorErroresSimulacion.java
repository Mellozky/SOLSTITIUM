package com.solstitium.solstitium.exception;

import com.solstitium.solstitium.controller.SimulacionController;
import com.solstitium.solstitium.model.dto.RespuestaErrorSimulacion;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Se limita al controlador de simulación para conservar el contrato de autenticación. */
@Slf4j
@RestControllerAdvice(assignableTypes = SimulacionController.class)
public class ManejadorErroresSimulacion {

    @ExceptionHandler(DatosSimulacionInvalidosException.class)
    public ResponseEntity<RespuestaErrorSimulacion> datosInvalidos(DatosSimulacionInvalidosException excepcion) {
        return respuesta(HttpStatus.BAD_REQUEST, "Revise los datos de la simulación.", excepcion.getErrores());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaErrorSimulacion> camposInvalidos(MethodArgumentNotValidException excepcion) {
        List<String> errores = excepcion.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).sorted().toList();
        return respuesta(HttpStatus.BAD_REQUEST, "Revise los datos de la simulación.", errores);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespuestaErrorSimulacion> formatoInvalido() {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo JSON falta o tiene un formato inválido.",
                List.of("Compruebe los campos y sus tipos: estrato e identificadores deben ser números enteros."));
    }

    @ExceptionHandler({IllegalStateException.class, DataAccessException.class})
    public ResponseEntity<RespuestaErrorSimulacion> servicioNoDisponible(Exception excepcion) {
        log.error("No se pudo cargar o utilizar la configuración de la simulación.", excepcion);
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "No es posible realizar la simulación en este momento. Intente más tarde.", List.of());
    }

    @ExceptionHandler({HttpMediaTypeNotSupportedException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<RespuestaErrorSimulacion> protocoloInvalido(Exception excepcion) {
        HttpStatus estado = excepcion instanceof HttpMediaTypeNotSupportedException
                ? HttpStatus.UNSUPPORTED_MEDIA_TYPE : HttpStatus.METHOD_NOT_ALLOWED;
        return respuesta(estado, "Use POST y Content-Type application/json para solicitar una simulación.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaErrorSimulacion> errorInesperado(Exception excepcion) {
        log.error("Error inesperado al realizar una simulación.", excepcion);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la simulación. Intente nuevamente.", List.of());
    }

    private ResponseEntity<RespuestaErrorSimulacion> respuesta(HttpStatus estado, String mensaje, List<String> errores) {
        return ResponseEntity.status(estado).body(new RespuestaErrorSimulacion(mensaje, List.copyOf(errores)));
    }
}
