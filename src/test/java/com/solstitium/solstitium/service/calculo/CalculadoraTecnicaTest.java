package com.solstitium.solstitium.service.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.dto.RespuestaSombra;
import com.solstitium.solstitium.model.dto.ResultadoConfiguracion;
import com.solstitium.solstitium.model.dto.SolicitudSimulacion;
import com.solstitium.solstitium.repository.ParametroRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** RF-04: columnas técnicas de CP-01 a CP-09 y salida sin espacio de CV-29. */
@SpringBootTest
@Transactional
class CalculadoraTecnicaTest {

    @Autowired
    private CalculadoraTecnica calculadora;

    @Autowired
    private CalculadoraSombra calculadoraSombra;

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
            "160|150|140,40,1,1,1,central,3,1.65,1.5,6600000",
            "220|200|210|230,40,2,1,1,central,4,2.20,2,8800000",
            "250,40,3,1,2,central,5,2.75,2.5,11000000",
            "300|320|280,40,2,2,3,central,7,3.85,3.5,15400000",
            "300|320|280,40,2,2,3,mlpe,7,3.85,3.5,17710000",
            "400|420,60,1,4,1,central,9,4.95,4.5,19800000",
            "400|420,60,1,4,1,mlpe,9,4.95,4.5,22770000",
            "500|520|480,8,1,1,1,central,2,1.10,1,4400000",
            "300,5,1,1,1,central,1,0.55,0.5,2200000",
            "100,40,4,4,4,central,3,1.65,1.5,6600000",
            "100,40,4,4,4,mlpe,3,1.65,1.5,7590000"
    })
    void reproduceElDimensionamientoDelErs(String consumos, String area, int manana, int mediodia, int tarde,
                                          String codigo, int paneles, String potencia, String inversor, String costo) {
        var resultado = dimensionar(solicitud(consumos, area, manana, mediodia, tarde), codigo, conjunto);
        assertThat(resultado.getConfiguracion()).isEqualTo(codigo);
        assertThat(resultado.getPaneles()).isEqualTo(paneles);
        assertThat(resultado.getPotenciaKwp()).isEqualByComparingTo(potencia);
        assertThat(resultado.getInversorKw()).isEqualByComparingTo(inversor);
        assertThat(resultado.getCostoTotal()).isEqualByComparingTo(costo);
        assertThat(resultado.getAhorroMensual()).isNull();
        assertThat(resultado.getTiempoRecuperacion()).isNull();
        assertThat(resultado.getRoi()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"central,14400000", "mlpe,16560000"})
    void cambiarElPanelDeReferenciaReproduceCp09(String codigo, String costo) {
        consultas.update("update panel set es_referencia = false where es_referencia");
        consultas.update("update panel set es_referencia = true where modelo = 'Alterno ERS 450'");
        gestorEntidades.clear();
        var resultado = dimensionar(solicitud("300|320|280", "40", 2, 2, 3), codigo, repositorio.obtenerParametros());
        assertThat(resultado.getPaneles()).isEqualTo(8);
        assertThat(resultado.getPotenciaKwp()).isEqualByComparingTo("3.60");
        assertThat(resultado.getInversorKw()).isEqualByComparingTo("3");
        assertThat(resultado.getCostoTotal()).isEqualByComparingTo(costo);
    }

    @Test
    void techoSinEspacioNoCalculaIndicadoresFinancierosCv29() {
        var resultado = dimensionar(solicitud("300", "3", 1, 1, 1), "central", conjunto);
        assertThat(resultado.getPaneles()).isZero();
        assertThat(resultado.getPotenciaKwp()).isZero();
        assertThat(resultado.getInversorKw()).isZero();
        assertThat(resultado.getCostoTotal()).isZero();
        assertThat(resultado.getEnergiaMensual()).isNull();
        assertThat(resultado.getCobertura()).isNull();
        assertThat(resultado.getTiempoRecuperacion()).isNull();
        assertThat(resultado.getClasificacion()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"59.4,1", "59.4001,2", "118.8,2"})
    void noAgregaUnPanelExtraCuandoElCocienteEsExacto(String consumo, int esperados) {
        var resultado = dimensionar(solicitud(consumo, "40", 1, 1, 1), "central", conjunto);
        assertThat(resultado.getPaneles()).isEqualTo(esperados);
    }

    @Test
    void noAumentaUnInversorQueYaEsMultiploDelPaso() {
        consultas.update("update parametro set valor = 1.1 where clave = 'relacion_cc_ca'");
        gestorEntidades.clear();
        var resultado = dimensionar(solicitud("59.4", "40", 1, 1, 1), "central", repositorio.obtenerParametros());
        assertThat(resultado.getInversorKw()).isEqualByComparingTo("0.5");
    }

    @ParameterizedTest
    @CsvSource({"hsp,5,6", "factor_rendimiento,0.9,6", "tope_sobredimensionamiento,0,6"})
    void usaLosParametrosTecnicosActualizados(String clave, String valor, int paneles) {
        consultas.update("update parametro set valor = ? where clave = ?", new BigDecimal(valor), clave);
        gestorEntidades.clear();
        var resultado = dimensionar(solicitud("300", "40", 2, 2, 3), "central", repositorio.obtenerParametros());
        assertThat(resultado.getPaneles()).isEqualTo(paneles);
    }

    @Test
    void usaElAprovechamientoYLaRelacionCcCaConfigurados() {
        consultas.update("update parametro set valor = 0.5 where clave = 'factor_aprovechamiento'");
        consultas.update("update parametro set valor = 1 where clave = 'relacion_cc_ca'");
        gestorEntidades.clear();
        var resultado = dimensionar(solicitud("500", "8", 1, 1, 1), "central", repositorio.obtenerParametros());
        assertThat(resultado.getPaneles()).isEqualTo(1);
        assertThat(resultado.getInversorKw()).isEqualByComparingTo("1");
    }

    @Test
    void usaElCostoActualizadoSinCambiarLosPaneles() {
        consultas.update("update configuracion set costo_kwp = 5000000 where codigo = 'central'");
        gestorEntidades.clear();
        var resultado = dimensionar(solicitud("300", "40", 2, 2, 3), "central", repositorio.obtenerParametros());
        assertThat(resultado.getPaneles()).isEqualTo(7);
        assertThat(resultado.getCostoTotal()).isEqualByComparingTo("19250000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"hsp", "factor_rendimiento", "relacion_cc_ca"})
    void rechazaDivisoresCeroSinProducirResultados(String clave) {
        consultas.update("update parametro set valor = 0 where clave = ?", clave);
        gestorEntidades.clear();
        ConjuntoParametros invalidos = repositorio.obtenerParametros();
        assertThatThrownBy(() -> dimensionar(solicitud("300", "40", 1, 1, 1), "central", invalidos))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("positivo");
    }

    private ResultadoConfiguracion dimensionar(SolicitudSimulacion solicitud, String codigo, ConjuntoParametros parametros) {
        var sombra = calculadoraSombra.calcularFactor(solicitud.getRespuestasSombra(), parametros);
        var configuracion = sombra.getTramo().getConfiguraciones().stream()
                .filter(actual -> actual.getCodigo().equals(codigo)).findFirst().orElseThrow();
        return calculadora.dimensionar(solicitud, sombra, configuracion, parametros);
    }

    private SolicitudSimulacion solicitud(String consumos, String area, int manana, int mediodia, int tarde) {
        SolicitudSimulacion solicitud = new SolicitudSimulacion();
        solicitud.setConsumos(Arrays.stream(consumos.split("\\|")).map(BigDecimal::new).toList());
        solicitud.setAreaLibre(new BigDecimal(area));
        solicitud.setEstrato(4);
        int[] porciones = {manana, mediodia, tarde};
        solicitud.setRespuestasSombra(List.of(respuesta(1, porciones[0]), respuesta(2, porciones[1]), respuesta(3, porciones[2])));
        return solicitud;
    }

    private RespuestaSombra respuesta(long franja, long porcion) {
        RespuestaSombra respuesta = new RespuestaSombra();
        respuesta.setFranjaId(franja);
        respuesta.setPorcionId(porcion);
        return respuesta;
    }
}
