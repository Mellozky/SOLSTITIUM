package com.solstitium.solstitium.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.solstitium.solstitium.apoyo.SolicitudesPrueba;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SimulacionServiceTest {
    @Autowired private SimulacionService servicio;
    @Autowired private JdbcTemplate consultas;

    @ParameterizedTest
    @CsvSource({"1,150,40,1,1,1,central", "4,300,40,2,2,3,central",
            "5,410,60,1,4,1,mlpe", "1,100,40,4,4,4,mlpe"})
    void recomiendaSegunLaRecuperacionDelErs(int estrato, String consumo, String area,
                                           int manana, int mediodia, int tarde, String recomendada) {
        var solicitud = SolicitudesPrueba.crear(estrato, consumo, area, manana, mediodia, tarde);
        var resultado = servicio.simular(solicitud);
        assertThat(resultado.getRecomendada()).isEqualTo(recomendada);
        assertThat(resultado.getPanelReferencia()).contains("DEMOSTRACIÓN");
        assertThat(resultado.getFechaTarifa()).isNotNull();
        assertThat(resultado.getAdvertencias()).contains("Esta simulación es una estimación, no una cotización.");
        assertThat(solicitud.getRespuestasSombra().getFirst().getPorcionId()).isEqualTo((long) manana);
    }

    @ParameterizedTest
    @CsvSource({"4,300,40,2,2,3,6,300000,3.44", "5,410,60,1,4,1,7,492000,2.51",
            "1,100,40,4,4,4,2,50000,6.40"})
    void recalculaElEscenarioSinSombra(int estrato, String consumo, String area, int manana,
                                     int mediodia, int tarde, int paneles, String ahorro, String anios) {
        var respuesta = servicio.simular(SolicitudesPrueba.crear(estrato, consumo, area, manana, mediodia, tarde));
        var escenario = respuesta.getEscenarioSinSombra();
        assertThat(escenario.getPaneles()).isEqualTo(paneles);
        assertThat(escenario.getAhorroMensual()).isEqualByComparingTo(ahorro);
        assertThat(escenario.getTiempoRecuperacion()).isCloseTo(new BigDecimal(anios), within(new BigDecimal("0.005")));
    }

    @Test
    void sinSombraNoCreaUnEscenarioDuplicado() {
        assertThat(servicio.simular(SolicitudesPrueba.crear(1, "150", "40", 1, 1, 1)).getEscenarioSinSombra()).isNull();
    }

    @Test
    void siNingunaRecuperaNoRecomiendaNinguna() {
        consultas.update("update parametro set valor = 1 where clave = 'vida_util'");
        var respuesta = servicio.simular(SolicitudesPrueba.crear(4, "300", "40", 2, 2, 3));
        assertThat(respuesta.getRecomendada()).isNull();
        assertThat(respuesta.getMotivoRecomendacion()).contains("Ninguna configuración");
    }

    @Test
    void sinEspacioNoDevuelveResultadosFinancieros() {
        consultas.update("update panel set area_m2 = 20 where es_referencia");
        var respuesta = servicio.simular(SolicitudesPrueba.crear(4, "300", "5", 1, 1, 1));
        assertThat(respuesta.isSinEspacio()).isTrue();
        assertThat(respuesta.getResultados()).isEmpty();
        assertThat(respuesta.getRecomendada()).isNull();
        assertThat(respuesta.getEscenarioSinSombra()).isNull();
    }

    @Test
    void cambiarReferenciaCambiaLaRecomendacionCp09() {
        consultas.update("update panel set es_referencia = false where es_referencia");
        consultas.update("update panel set es_referencia = true where modelo = 'Alterno ERS 450'");
        var respuesta = servicio.simular(SolicitudesPrueba.crear(4, "300", "40", 2, 2, 3));
        assertThat(respuesta.getRecomendada()).isEqualTo("mlpe");
        assertThat(respuesta.getResultados()).allSatisfy(resultado -> assertThat(resultado.getPaneles()).isEqualTo(8));
    }
}
