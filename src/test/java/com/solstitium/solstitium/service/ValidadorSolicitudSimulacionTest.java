package com.solstitium.solstitium.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solstitium.solstitium.exception.DatosSimulacionInvalidosException;
import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.dto.RespuestaSombra;
import com.solstitium.solstitium.model.dto.SolicitudSimulacion;
import com.solstitium.solstitium.repository.ParametroRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Límites CV-01–CV-12 y CV-15, y contrato de entrada con parámetros reales de V2. */
@SpringBootTest
@Transactional
class ValidadorSolicitudSimulacionTest {

    @Autowired
    private ValidadorSolicitudSimulacion validador;

    @Autowired
    private ParametroRepository repositorio;

    @Autowired
    private JdbcTemplate consultas;

    @Autowired
    private EntityManager gestorEntidades;

    private ConjuntoParametros conjunto;

    @BeforeEach
    void cargarParametrosDelErs() {
        conjunto = repositorio.obtenerParametros();
    }

    @ParameterizedTest
    @CsvSource({"800,false", "801,true", "2000,true"})
    void respetaUmbralYTopeDeConsumo(String consumo, boolean advierte) {
        SolicitudSimulacion solicitud = solicitudValida();
        solicitud.setConsumos(List.of(new BigDecimal(consumo)));
        assertThat(validador.validar(solicitud, conjunto)).hasSize(advierte ? 1 : 0);
    }

    @ParameterizedTest
    @MethodSource("consumosInvalidos")
    void rechazaConsumosVaciosNoPositivosOSuperioresAlTope(List<BigDecimal> consumos) {
        SolicitudSimulacion solicitud = solicitudValida();
        solicitud.setConsumos(consumos);
        assertThatThrownBy(() -> validador.validar(solicitud, conjunto))
                .isInstanceOf(DatosSimulacionInvalidosException.class);
    }

    static Stream<List<BigDecimal>> consumosInvalidos() {
        return Stream.of(null, List.<BigDecimal>of(), Arrays.asList((BigDecimal) null),
                List.of(BigDecimal.ZERO), List.of(new BigDecimal("-10")),
                List.of(new BigDecimal("2001")), List.of(new BigDecimal("2000.01")));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 12})
    void admiteDeUnoADoceMeses(int meses) {
        SolicitudSimulacion solicitud = solicitudValida();
        solicitud.setConsumos(Collections.nCopies(meses, new BigDecimal("150")));
        assertThat(validador.validar(solicitud, conjunto)).isEmpty();
    }

    @Test
    void rechazaUnDecimotercerMes() {
        SolicitudSimulacion solicitud = solicitudValida();
        solicitud.setConsumos(Collections.nCopies(13, new BigDecimal("150")));
        assertThatThrownBy(() -> validador.validar(solicitud, conjunto))
                .isInstanceOf(DatosSimulacionInvalidosException.class).hasMessageContaining("12 meses");
    }

    @ParameterizedTest
    @CsvSource({"4.99,false", "5,true", "500,true", "500.01,false"})
    void respetaLosExtremosInclusivosDelArea(String area, boolean acepta) {
        SolicitudSimulacion solicitud = solicitudValida();
        solicitud.setAreaLibre(new BigDecimal(area));
        if (acepta) {
            assertThat(validador.validar(solicitud, conjunto)).isEmpty();
        } else {
            assertThatThrownBy(() -> validador.validar(solicitud, conjunto))
                    .isInstanceOf(DatosSimulacionInvalidosException.class).hasMessageContaining("área libre");
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 7})
    void exigeUnEstratoValido(Integer estrato) {
        SolicitudSimulacion solicitud = solicitudValida();
        solicitud.setEstrato(estrato);
        assertThatThrownBy(() -> validador.validar(solicitud, conjunto))
                .isInstanceOf(DatosSimulacionInvalidosException.class).hasMessageContaining("estrato");
    }

    @ParameterizedTest
    @ValueSource(strings = {"faltante", "repetida", "franja_desconocida", "porcion_desconocida", "nula", "sin_porcion", "sin_lista"})
    void exigeUnaRespuestaValidaPorCadaFranja(String escenario) {
        SolicitudSimulacion solicitud = solicitudValida();
        switch (escenario) {
            case "faltante" -> solicitud.getRespuestasSombra().removeLast();
            case "repetida" -> solicitud.getRespuestasSombra().get(1).setFranjaId(1L);
            case "franja_desconocida" -> solicitud.getRespuestasSombra().getFirst().setFranjaId(999L);
            case "porcion_desconocida" -> solicitud.getRespuestasSombra().getFirst().setPorcionId(999L);
            case "nula" -> solicitud.getRespuestasSombra().set(0, null);
            case "sin_porcion" -> solicitud.getRespuestasSombra().getFirst().setPorcionId(null);
            case "sin_lista" -> solicitud.setRespuestasSombra(null);
            default -> throw new IllegalArgumentException("Escenario de prueba desconocido.");
        }
        assertThatThrownBy(() -> validador.validar(solicitud, conjunto))
                .isInstanceOf(DatosSimulacionInvalidosException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rechazaUnaFacturaNoPositiva(String factura) {
        SolicitudSimulacion solicitud = solicitudValida();
        solicitud.setValorFactura(new BigDecimal(factura));
        assertThatThrownBy(() -> validador.validar(solicitud, conjunto))
                .isInstanceOf(DatosSimulacionInvalidosException.class).hasMessageContaining("factura");
    }

    @Test
    void aplicaCambiosDeLimitesDesdePostgres() {
        consultas.update("update parametro set valor = 700 where clave = 'consumo_maximo'");
        consultas.update("update parametro set valor = 600 where clave = 'umbral_advertencia_consumo'");
        consultas.update("update parametro set valor = 20 where clave = 'area_minima'");
        gestorEntidades.clear();
        ConjuntoParametros nuevos = repositorio.obtenerParametros();
        SolicitudSimulacion solicitud = solicitudValida();
        solicitud.setConsumos(List.of(new BigDecimal("701")));
        solicitud.setAreaLibre(new BigDecimal("19.99"));

        assertThatThrownBy(() -> validador.validar(solicitud, nuevos))
                .isInstanceOf(DatosSimulacionInvalidosException.class)
                .hasMessageContaining("700").hasMessageContaining("20");
        solicitud.setConsumos(List.of(new BigDecimal("601")));
        solicitud.setAreaLibre(new BigDecimal("20"));
        assertThat(validador.validar(solicitud, nuevos)).hasSize(1);
    }

    @Test
    void respondeATodasLasFranjasDelCatalogoEnLugarDeFijarSusIds() {
        consultas.update("update franja_horaria set id = 10 where id = 1");
        gestorEntidades.clear();
        ConjuntoParametros nuevos = repositorio.obtenerParametros();
        SolicitudSimulacion solicitud = solicitudValida();
        solicitud.getRespuestasSombra().getFirst().setFranjaId(10L);
        assertThat(validador.validar(solicitud, nuevos)).isEmpty();
    }

    @Test
    void calculaElPromedioSinRedondearLosConsumosIndividuales() {
        SolicitudSimulacion solicitud = solicitudValida();
        solicitud.setConsumos(List.of(new BigDecimal("160.25"), new BigDecimal("150.25"), new BigDecimal("140.25")));
        assertThat(solicitud.consumoPromedio()).isEqualByComparingTo("150.25");
    }

    @Test
    void leeElContratoJsonSinCamposTecnicosDeSombra() throws Exception {
        SolicitudSimulacion solicitud = JsonMapper.builder().build().readValue("""
                {"consumos":[160,150,140],"estrato":1,"areaLibre":40,
                 "respuestasSombra":[{"franjaId":1,"porcionId":1},
                 {"franjaId":2,"porcionId":1},{"franjaId":3,"porcionId":1}]}
                """, SolicitudSimulacion.class);
        assertThat(solicitud.getValorFactura()).isNull();
        assertThat(validador.validar(solicitud, conjunto)).isEmpty();
        assertThat(solicitud.consumoPromedio()).isEqualByComparingTo("150");
    }

    @Test
    void rechazaUnaSolicitudAusente() {
        assertThatThrownBy(() -> validador.validar(null, conjunto))
                .isInstanceOf(DatosSimulacionInvalidosException.class).hasMessageContaining("Debe enviar");
    }

    private SolicitudSimulacion solicitudValida() {
        SolicitudSimulacion solicitud = new SolicitudSimulacion();
        solicitud.setConsumos(List.of(new BigDecimal("150")));
        solicitud.setEstrato(1);
        solicitud.setAreaLibre(new BigDecimal("40"));
        List<RespuestaSombra> respuestas = new ArrayList<>();
        for (long franja = 1; franja <= 3; franja++) {
            RespuestaSombra respuesta = new RespuestaSombra();
            respuesta.setFranjaId(franja);
            respuesta.setPorcionId(1L);
            respuestas.add(respuesta);
        }
        solicitud.setRespuestasSombra(respuestas);
        return solicitud;
    }
}
