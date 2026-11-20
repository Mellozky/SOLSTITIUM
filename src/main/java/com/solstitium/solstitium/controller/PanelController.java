package com.solstitium.solstitium.controller;

import com.solstitium.solstitium.model.dto.*;
import com.solstitium.solstitium.service.PanelService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/paneles")
@RequiredArgsConstructor
public class PanelController {
    private final PanelService servicio;

    @GetMapping
    public ConsultaPaneles listar() { return servicio.listar(); }

    @PostMapping
    public ResponseEntity<PanelCatalogo> registrar(@Valid @RequestBody SolicitudPanel solicitud) {
        var panel = servicio.registrar(solicitud);
        return ResponseEntity.created(URI.create("/api/admin/paneles/" + panel.id())).body(panel);
    }

    @PutMapping("/{id}")
    public PanelCatalogo editar(@PathVariable Long id, @Valid @RequestBody SolicitudPanel solicitud) {
        return servicio.editar(id, solicitud);
    }

    @PutMapping("/{id}/estado")
    public PanelCatalogo cambiarEstado(@PathVariable Long id, @Valid @RequestBody SolicitudEstadoPanel solicitud) {
        return servicio.cambiarEstado(id, solicitud);
    }

    @PutMapping("/{id}/referencia")
    public PanelCatalogo designarReferencia(@PathVariable Long id, @Valid @RequestBody SolicitudReferenciaPanel solicitud) {
        return servicio.designarReferencia(id, solicitud);
    }
}
