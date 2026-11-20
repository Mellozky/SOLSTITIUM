package com.solstitium.solstitium.controller;

import com.solstitium.solstitium.model.dto.ConsultaParametros;
import com.solstitium.solstitium.model.dto.SolicitudActualizacionParametros;
import com.solstitium.solstitium.service.ParametroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/parametros")
@RequiredArgsConstructor
public class ParametroController {
    private final ParametroService servicio;

    @GetMapping
    public ConsultaParametros consultar(@RequestParam String seccion) { return servicio.consultar(seccion); }

    @PutMapping
    public ResultadoActualizacion actualizar(@Valid @RequestBody SolicitudActualizacionParametros solicitud) {
        return new ResultadoActualizacion(servicio.actualizar(solicitud));
    }

    public record ResultadoActualizacion(int modificados) {}
}
