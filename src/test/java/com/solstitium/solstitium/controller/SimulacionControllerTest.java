package com.solstitium.solstitium.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SimulacionControllerTest {
    @Autowired private MockMvc cliente;

    private static final String SOLICITUD = """
            {"consumos":[160,150,140],"estrato":1,"areaLibre":40,
             "respuestasSombra":[{"franjaId":1,"porcionId":1},
             {"franjaId":2,"porcionId":1},{"franjaId":3,"porcionId":1}]}
            """;

    @Test
    void simulaSinRegistroSesionNiTokenCsrf() throws Exception {
        cliente.perform(post("/api/simulaciones").contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recomendada").value("central"))
                .andExpect(jsonPath("$.resultados[0].paneles").value(3))
                .andExpect(jsonPath("$.resultados[0].ahorroMensual").value(75000))
                .andExpect(jsonPath("$.gastoMensualSinSistema").value(75000))
                .andExpect(jsonPath("$.resultados[0].gastoMensualConSistema").value(0))
                .andExpect(jsonPath("$.fechaTarifa").isNotEmpty());
    }

    @Test
    void consultaParametrosDeEntradaSinSesion() throws Exception {
        cliente.perform(get("/api/simulaciones/parametros-entrada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumoMaximo").value(2000))
                .andExpect(jsonPath("$.umbralAdvertenciaConsumo").value(800))
                .andExpect(jsonPath("$.areaMinima").value(5))
                .andExpect(jsonPath("$.areaMaxima").value(500))
                .andExpect(jsonPath("$.estratos.length()").value(6))
                .andExpect(jsonPath("$.estratos[0].tipoAjuste").value("SUBSIDIO"));
    }

    @Test
    void consultaCatalogoPublicoDeSombraEnElOrdenConfigurado() throws Exception {
        String[][] franjasEsperadas = {
                {"Mañana", "06:00:00", "10:00:00"},
                {"Mediodía", "10:00:00", "14:00:00"},
                {"Tarde", "14:00:00", "18:00:00"}
        };
        String[] porcionesEsperadas = {"Nada", "Un borde o una esquina", "Cerca de una cuarta parte", "La mitad o más"};
        var respuesta = cliente.perform(get("/api/simulaciones/parametros-entrada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas.length()").value(franjasEsperadas.length))
                .andExpect(jsonPath("$.porciones.length()").value(porcionesEsperadas.length));
        for (int indice = 0; indice < franjasEsperadas.length; indice++) {
            String ruta = "$.franjas[" + indice + "]";
            respuesta.andExpect(jsonPath(ruta + ".id").value(indice + 1))
                    .andExpect(jsonPath(ruta + ".nombre").value(franjasEsperadas[indice][0]))
                    .andExpect(jsonPath(ruta + ".horaInicio").value(franjasEsperadas[indice][1]))
                    .andExpect(jsonPath(ruta + ".horaFin").value(franjasEsperadas[indice][2]))
                    .andExpect(jsonPath(ruta + ".peso").doesNotExist());
        }
        for (int indice = 0; indice < porcionesEsperadas.length; indice++) {
            String ruta = "$.porciones[" + indice + "]";
            respuesta.andExpect(jsonPath(ruta + ".id").value(indice + 1))
                    .andExpect(jsonPath(ruta + ".etiqueta").value(porcionesEsperadas[indice]))
                    .andExpect(jsonPath(ruta + ".valor").doesNotExist());
        }
    }

    @Test
    void muestraElTramoDeCp04SinSolicitarUnaSimulacionCompleta() throws Exception {
        cliente.perform(post("/api/simulaciones/vista-previa-sombra").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"respuestasSombra":[{"franjaId":1,"porcionId":2},
                                {"franjaId":2,"porcionId":2},{"franjaId":3,"porcionId":3}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tramo").value("Alta"));
    }

    @Test
    void rechazaLaVistaPreviaSiFaltaUnaFranja() throws Exception {
        cliente.perform(post("/api/simulaciones/vista-previa-sombra").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"respuestasSombra":[{"franjaId":1,"porcionId":1},{"franjaId":2,"porcionId":1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0]").value("Debe responder una vez cada franja de sombra configurada."));
    }

    @Test
    void calculaPromedioSinFacturaSombraNiSesion() throws Exception {
        cliente.perform(post("/api/simulaciones/promedio-consumo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consumos\":[120,150]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(135));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[0]", "[null]", "[2001]"})
    void rechazaConsumosInvalidosEnElPromedio(String consumos) throws Exception {
        cliente.perform(post("/api/simulaciones/promedio-consumo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consumos\":" + consumos + "}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "null", "[]"})
    void rechazaCuerposInvalidosEnEspanol(String cuerpo) throws Exception {
        cliente.perform(post("/api/simulaciones").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.errores").isArray());
    }

    @Test
    void rechazaConsumoSuperiorAlTope() throws Exception {
        cliente.perform(post("/api/simulaciones").contentType(MediaType.APPLICATION_JSON)
                        .content(SOLICITUD.replace("160,150,140", "2001")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0]").isNotEmpty());
    }

    @Test
    void noTruncaUnEstratoDecimal() throws Exception {
        cliente.perform(post("/api/simulaciones").contentType(MediaType.APPLICATION_JSON)
                        .content(SOLICITUD.replace("\"estrato\":1", "\"estrato\":1.5")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginSigueExigiendoTokenCsrf() throws Exception {
        cliente.perform(post("/api/autenticacion/iniciar-sesion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"nadie\",\"contrasena\":\"prueba\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void permitePreflightDelOrigenLocal() throws Exception {
        cliente.perform(options("/api/simulaciones").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void rechazaPreflightDeUnOrigenNoAutorizado() throws Exception {
        cliente.perform(options("/api/simulaciones").header("Origin", "https://origen-no-autorizado.invalid")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
