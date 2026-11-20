package com.solstitium.solstitium.service.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solstitium.solstitium.exception.DatosSimulacionInvalidosException;
import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.dto.RespuestaSombra;
import com.solstitium.solstitium.model.entity.Configuracion;
import com.solstitium.solstitium.repository.ParametroRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
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

/** RF-03: factores del ERS, extremos de tramos y parámetros modificados en PostgreSQL. */
@SpringBootTest
@Transactional
class CalculadoraSombraTest {

    @Autowired
    private CalculadoraSombra calculadora;

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
    @CsvSource({"1,1,1,1,Sin sombra", "2,1,1,0.9550,Leve", "3,1,2,0.8425,Moderada",
            "2,2,3,0.6325,Alta", "1,4,1,0.3000,Severa", "4,4,4,0,Severa"})
    void reproduceLosFactoresYTramosDeLosCasosDelErs(int manana, int mediodia, int tarde,
                                                   String esperado, String tramo) {
        var resultado = calculadora.calcularFactor(respuestas(manana, mediodia, tarde), conjunto);

        assertThat(resultado.getFactor()).isEqualByComparingTo(esperado);
        assertThat(resultado.getPerdida().add(resultado.getFactor())).isEqualByComparingTo("1");
        assertThat(resultado.getTramo().getNombre()).isEqualTo(tramo);
        assertThat(resultado.getTramo().isMuestraAdvertencia()).isEqualTo(tramo.equals("Severa"));
        assertThat(resultado.getTramo().getConfiguraciones()).extracting(Configuracion::getCodigo)
                .containsExactly(tramo.equals("Alta") || tramo.equals("Severa")
                        ? new String[]{"central", "mlpe"} : new String[]{"central"});
    }

    @ParameterizedTest
    @CsvSource({"0,Severa", "0.5499,Severa", "0.55,Alta", "0.7499,Alta", "0.75,Moderada",
            "0.8999,Moderada", "0.90,Leve", "0.9999,Leve", "1,Sin sombra"})
    void respetaLosExtremosSinRedondearElFactor(String factor, String tramo) {
        assertThat(calculadora.clasificarTramo(new BigDecimal(factor), conjunto).getNombre()).isEqualTo(tramo);
    }

    @Test
    void acotaLaPerdidaDentroDeCadaFranja() {
        var resultado = calculadora.calcularFactor(respuestas(1, 1, 4), conjunto);
        assertThat(resultado.getPerdida()).isEqualByComparingTo("0.15");
        assertThat(resultado.getFactor()).isEqualByComparingTo("0.85");
    }

    @Test
    void asociaLasRespuestasPorIdentificadorEnLugarDeSuPosicion() {
        var resultado = calculadora.calcularFactor(respuestas(3, 1, 2).reversed(), conjunto);
        assertThat(resultado.getFactor()).isEqualByComparingTo("0.8425");
    }

    @ParameterizedTest
    @ValueSource(strings = {"faltante", "duplicada", "franja_desconocida", "porcion_desconocida", "nula", "sin_porcion"})
    void rechazaRespuestasInvalidasSinProducirUnFactor(String escenario) {
        List<RespuestaSombra> entradas = respuestas(1, 1, 1);
        switch (escenario) {
            case "faltante" -> entradas.removeLast();
            case "duplicada" -> entradas.get(1).setFranjaId(1L);
            case "franja_desconocida" -> entradas.getFirst().setFranjaId(999L);
            case "porcion_desconocida" -> entradas.getFirst().setPorcionId(999L);
            case "nula" -> entradas.set(0, null);
            case "sin_porcion" -> entradas.getFirst().setPorcionId(null);
            default -> throw new IllegalArgumentException("Escenario desconocido.");
        }
        assertThatThrownBy(() -> calculadora.calcularFactor(entradas, conjunto))
                .isInstanceOf(DatosSimulacionInvalidosException.class);
    }

    @Test
    void rechazaUnaListaNula() {
        assertThatThrownBy(() -> calculadora.calcularFactor(null, conjunto))
                .isInstanceOf(DatosSimulacionInvalidosException.class);
    }

    @ParameterizedTest
    @CsvSource({"2,0.755", "0,1"})
    void usaLaAmplificacionActualizada(String amplificacion, String esperado) {
        consultas.update("update parametro set valor = ? where clave = 'amplificacion_serie'", new BigDecimal(amplificacion));
        gestorEntidades.clear();
        var resultado = calculadora.calcularFactor(respuestas(2, 2, 3), repositorio.obtenerParametros());
        assertThat(resultado.getFactor()).isEqualByComparingTo(esperado);
    }

    @Test
    void usaPesosYPorcionesActualizados() {
        consultas.update("update franja_horaria set peso = case id when 1 then 0.2 when 2 then 0.6 else 0.2 end");
        consultas.update("update porcion_afectada set valor = 0.2 where id = 2");
        gestorEntidades.clear();
        var resultado = calculadora.calcularFactor(respuestas(2, 1, 1), repositorio.obtenerParametros());
        assertThat(resultado.getFactor()).isEqualByComparingTo("0.88");
    }

    @Test
    void usaUmbralesYConfiguracionesActualizadas() {
        consultas.update("update tramo_sombreado set factor_minimo = 0.96 where id = 2");
        consultas.update("update tramo_sombreado set factor_maximo = 0.96 where id = 3");
        consultas.update("insert into tramo_configuracion(tramo_id, configuracion_id) values (3, 2)");
        gestorEntidades.clear();
        var resultado = calculadora.calcularFactor(respuestas(2, 1, 1), repositorio.obtenerParametros());
        assertThat(resultado.getTramo().getNombre()).isEqualTo("Moderada");
        assertThat(resultado.getTramo().getConfiguraciones()).extracting(Configuracion::getCodigo)
                .containsExactly("central", "mlpe");
    }

    @Test
    void rechazaPesosQueNoSumanUno() {
        consultas.update("update franja_horaria set peso = 0.6 where id = 2");
        gestorEntidades.clear();
        ConjuntoParametros incoherentes = repositorio.obtenerParametros();
        assertThatThrownBy(() -> calculadora.calcularFactor(respuestas(1, 1, 1), incoherentes))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sumar uno");
    }

    @ParameterizedTest
    @CsvSource({"0.96,0.955", "0.80,0.8425"})
    void rechazaClasificacionesAmbiguasOSinCobertura(String maximoModerado, String factor) {
        consultas.update("update tramo_sombreado set factor_maximo = ? where id = 3", new BigDecimal(maximoModerado));
        gestorEntidades.clear();
        ConjuntoParametros incoherentes = repositorio.obtenerParametros();
        assertThatThrownBy(() -> calculadora.clasificarTramo(new BigDecimal(factor), incoherentes))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exactamente a un tramo");
    }

    @Test
    void rechazaUnTramoSeleccionadoSinConfiguraciones() {
        consultas.update("delete from tramo_configuracion where tramo_id = 1");
        gestorEntidades.clear();
        ConjuntoParametros incompletos = repositorio.obtenerParametros();
        assertThatThrownBy(() -> calculadora.calcularFactor(respuestas(1, 1, 1), incompletos))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no tiene configuraciones");
    }

    private List<RespuestaSombra> respuestas(int manana, int mediodia, int tarde) {
        int[] porciones = {manana, mediodia, tarde};
        List<RespuestaSombra> respuestas = new ArrayList<>();
        for (int indice = 0; indice < porciones.length; indice++) {
            RespuestaSombra respuesta = new RespuestaSombra();
            respuesta.setFranjaId((long) indice + 1);
            respuesta.setPorcionId((long) porciones[indice]);
            respuestas.add(respuesta);
        }
        return respuestas;
    }
}
