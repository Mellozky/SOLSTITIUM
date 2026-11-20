package com.solstitium.solstitium.controller;

import com.solstitium.solstitium.model.dto.RegistroBitacora;
import com.solstitium.solstitium.service.BitacoraService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bitacora")
@RequiredArgsConstructor
public class BitacoraController {
    private final BitacoraService servicio;

    @GetMapping
    public List<RegistroBitacora> consultar() { return servicio.consultar(); }
}
