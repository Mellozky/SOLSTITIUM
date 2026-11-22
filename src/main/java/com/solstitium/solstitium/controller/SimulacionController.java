package com.solstitium.solstitium.controller;

import com.solstitium.solstitium.model.dto.ParametrosEntradaSimulacion;
import com.solstitium.solstitium.model.dto.ResultadoSimulacion;
import com.solstitium.solstitium.model.dto.SolicitudPromedioConsumo;
import com.solstitium.solstitium.model.dto.SolicitudSimulacion;
import com.solstitium.solstitium.service.SimulacionService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Puerta HTTP del ERS: recibe DTO y delega en un único servicio. */
@RestController
@RequestMapping("/api/simulaciones")
@RequiredArgsConstructor
public class SimulacionController {
    private final SimulacionService servicio;

    @PostMapping
    public ResultadoSimulacion simular(@Valid @RequestBody SolicitudSimulacion solicitud) {
        return servicio.simular(solicitud);
    }

    @GetMapping("/parametros-entrada")
    public ParametrosEntradaSimulacion consultarParametrosEntrada() {
        return servicio.consultarParametrosEntrada();
    }

    @PostMapping("/promedio-consumo")
    public BigDecimal calcularPromedioConsumo(@Valid @RequestBody SolicitudPromedioConsumo solicitud) {
        return servicio.calcularPromedioConsumo(solicitud.consumos());
    }
}
