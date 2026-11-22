package com.solstitium.solstitium.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ControladorInterfazTest {

    @Autowired
    private MockMvc clientePruebas;

    @ParameterizedTest
    @ValueSource(strings = {"/simulacion", "/simulacion/consumo", "/simulacion/resultados",
            "/admin", "/admin/ingreso", "/admin/tarifas"})
    void lasRutasDelNavegadorEntreganLaInterfazSinSesion(String ruta) throws Exception {
        clientePruebas.perform(get(ruta))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/parametros?seccion=tarifas", "/api/admin/bitacora", "/api/admin/paneles"})
    void laApiAdministrativaSigueExigiendoSesion(String ruta) throws Exception {
        clientePruebas.perform(get(ruta))
                .andExpect(status().isUnauthorized())
                .andExpect(forwardedUrl(null));
    }
}
