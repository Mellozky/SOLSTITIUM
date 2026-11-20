package com.solstitium.solstitium.service.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.dto.RespuestaSombra;
import com.solstitium.solstitium.model.dto.SolicitudSimulacion;
import com.solstitium.solstitium.repository.ParametroRepository;
import com.solstitium.solstitium.service.ValidadorSolicitudSimulacion;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** RF-01/RF-02 y CV-13/CV-14, con datos reales de V2; cada cambio se revierte. */
@SpringBootTest
@Transactional
class CalculadoraTarifaTest {

    @Autowired
    private CalculadoraTarifa calculadora;

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
    void cargarParametros() {
        conjunto = repositorio.obtenerParametros();
    }

    @ParameterizedTest
    @CsvSource({
            "1,100,50000", "2,100,60000", "3,100,85000", "4,100,100000", "5,100,120000", "6,100,120000",
            "1,173,86500", "2,173,103800", "3,173,147050", "4,173,173000", "5,173,207600", "6,173,207600",
            "1,250,163500", "2,250,180800", "3,250,224050", "4,250,250000", "5,250,300000", "6,250,300000"
    })
    void liquidaLosSeisEstratosAntesEnYDespuesDeSubsistencia(int estrato, String consumo, String esperado) {
        assertThat(calculadora.calcularCostoMensual(new BigDecimal(consumo), estrato, conjunto))
                .isEqualByComparingTo(esperado);
    }

    @Test
    void conservaLaPrecisionDeConsumosFraccionarios() {
        assertThat(calculadora.calcularCostoMensual(new BigDecimal("173.001"), 1, conjunto))
                .isEqualByComparingTo("86501");
    }

    @Test
    void permiteConsumoCeroParaCalculosInternos() {
        assertThat(calculadora.calcularCostoMensual(BigDecimal.ZERO, 1, conjunto)).isZero();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-1"})
    void rechazaConsumoNuloONegativo(String consumo) {
        assertThatThrownBy(() -> calculadora.calcularCostoMensual(
                consumo == null ? null : new BigDecimal(consumo), 1, conjunto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"230000,false", "268860,false", "268861,true", "179240,false", "179239,true", "224050,false"})
    void advierteSoloAlSuperarElUmbralEnCualquierSentido(String factura, boolean advierte) {
        SolicitudSimulacion solicitud = solicitud(3, "250");
        solicitud.setValorFactura(new BigDecimal(factura));

        assertThat(calculadora.obtenerAdvertenciaFactura(solicitud, conjunto).isPresent()).isEqualTo(advierte);
        assertThat(validador.validar(solicitud, conjunto)).hasSize(advierte ? 1 : 0);
        assertThat(calculadora.calcularCostoMensual(new BigDecimal("250"), 3, conjunto))
                .isEqualByComparingTo("224050");
    }

    @Test
    void comparaLaFacturaConElUltimoMesEnLugarDelPromedio() {
        SolicitudSimulacion solicitud = solicitud(5, "150", "900");
        solicitud.setValorFactura(new BigDecimal("1080000"));

        assertThat(solicitud.consumoPromedio()).isEqualByComparingTo("525");
        assertThat(calculadora.obtenerAdvertenciaFactura(solicitud, conjunto)).isEmpty();
        // Solo permanece la advertencia de consumo atípico del último mes.
        assertThat(validador.validar(solicitud, conjunto)).singleElement().asString().contains("Consumo poco común");
    }

    @Test
    void noComparaUnaFacturaOmitida() {
        assertThat(calculadora.obtenerAdvertenciaFactura(solicitud(1, "150"), conjunto)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"costo_unitario_kwh,1200,196200", "consumo_subsistencia,100,200000"})
    void usaElCostoYLaSubsistenciaActualizadosEnPostgres(String clave, String valor, String esperado) {
        consultas.update("update parametro set valor = ? where clave = ?", new BigDecimal(valor), clave);
        gestorEntidades.clear();

        assertThat(calculadora.calcularCostoMensual(new BigDecimal("250"), 1, repositorio.obtenerParametros()))
                .isEqualByComparingTo(esperado);
    }

    @Test
    void usaElPorcentajeActualizadoDelEstrato() {
        consultas.update("update estrato set porcentaje_ajuste = 60 where numero = 1");
        gestorEntidades.clear();

        assertThat(calculadora.calcularCostoMensual(new BigDecimal("250"), 1, repositorio.obtenerParametros()))
                .isEqualByComparingTo("146200");
    }

    @Test
    void usaElUmbralDeFacturaActualizadoEnPostgres() {
        consultas.update("update parametro set valor = 10 where clave = 'umbral_diferencia_factura'");
        gestorEntidades.clear();
        ConjuntoParametros actualizados = repositorio.obtenerParametros();
        SolicitudSimulacion solicitud = solicitud(3, "250");
        solicitud.setValorFactura(new BigDecimal("246455"));
        assertThat(calculadora.obtenerAdvertenciaFactura(solicitud, actualizados)).isEmpty();
        solicitud.setValorFactura(new BigDecimal("246456"));
        assertThat(calculadora.obtenerAdvertenciaFactura(solicitud, actualizados)).isPresent();
    }

    @Test
    void comparaSinDividirEntreCeroSiElSubsidioEsTotal() {
        consultas.update("update estrato set porcentaje_ajuste = 100 where numero = 1");
        gestorEntidades.clear();
        SolicitudSimulacion solicitud = solicitud(1, "100");
        solicitud.setValorFactura(BigDecimal.ONE);
        assertThat(calculadora.obtenerAdvertenciaFactura(solicitud, repositorio.obtenerParametros())).isPresent();
    }

    @Test
    void informaUnParametroAusenteSinInventarUnaTarifa() {
        consultas.update("delete from parametro where clave = 'costo_unitario_kwh'");
        gestorEntidades.clear();
        ConjuntoParametros incompletos = repositorio.obtenerParametros();
        assertThatThrownBy(() -> calculadora.calcularCostoMensual(new BigDecimal("150"), 1, incompletos))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("costo_unitario_kwh");
    }

    private SolicitudSimulacion solicitud(int estrato, String... consumos) {
        SolicitudSimulacion solicitud = new SolicitudSimulacion();
        solicitud.setEstrato(estrato);
        solicitud.setAreaLibre(new BigDecimal("40"));
        solicitud.setConsumos(List.of(consumos).stream().map(BigDecimal::new).toList());
        solicitud.setRespuestasSombra(IntStream.rangeClosed(1, 3).mapToObj(indice -> {
            RespuestaSombra respuesta = new RespuestaSombra();
            respuesta.setFranjaId((long) indice);
            respuesta.setPorcionId(1L);
            return respuesta;
        }).toList());
        return solicitud;
    }
}
