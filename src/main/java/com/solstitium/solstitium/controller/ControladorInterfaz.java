package com.solstitium.solstitium.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entrega la interfaz React en las rutas propias del navegador, de modo que residentes y
 * administradores usen la misma dirección. Las rutas de la API ({@code /api/**}) no pasan por aquí.
 */
@Controller
public class ControladorInterfaz {

    static final String PAGINA_INTERFAZ = "forward:/index.html";

    @GetMapping({"/simulacion", "/simulacion/**", "/admin", "/admin/**"})
    public String entregarInterfaz() {
        return PAGINA_INTERFAZ;
    }
}
