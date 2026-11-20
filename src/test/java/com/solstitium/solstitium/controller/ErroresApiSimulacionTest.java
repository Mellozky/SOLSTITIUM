package com.solstitium.solstitium.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solstitium.solstitium.service.SimulacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Fallos controlados: no se devuelve un resultado parcial ni detalles internos. */
@SpringBootTest
@AutoConfigureMockMvc
class ErroresApiSimulacionTest {
    @Autowired private MockMvc cliente;
    @MockitoBean private SimulacionService servicio;
    private static final String SOLICITUD = """
            {"consumos":[150],"estrato":1,"areaLibre":40,
             "respuestasSombra":[{"franjaId":1,"porcionId":1},
             {"franjaId":2,"porcionId":1},{"franjaId":3,"porcionId":1}]}
            """;

    @Test
    void configuracionInvalidaDevuelve503SinDetalles() throws Exception {
        when(servicio.simular(any())).thenThrow(new IllegalStateException("DETALLE_INTERNO"));
        comprobarFallo(503);
    }

    @Test
    void baseNoDisponibleDevuelve503SinResultadosCv16() throws Exception {
        when(servicio.simular(any())).thenThrow(new DataAccessResourceFailureException("DETALLE_INTERNO"));
        comprobarFallo(503);
    }

    @Test
    void errorInesperadoDevuelve500SinTraza() throws Exception {
        when(servicio.simular(any())).thenThrow(new RuntimeException("DETALLE_INTERNO"));
        comprobarFallo(500);
    }

    private void comprobarFallo(int estado) throws Exception {
        cliente.perform(post("/api/simulaciones").contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().is(estado)).andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.errores").isEmpty()).andExpect(jsonPath("$.resultados").doesNotExist())
                .andExpect(content().string(not(containsString("DETALLE_INTERNO"))));
    }
}
