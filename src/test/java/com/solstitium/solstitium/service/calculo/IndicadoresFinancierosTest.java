package com.solstitium.solstitium.service.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.solstitium.solstitium.apoyo.SolicitudesPrueba;
import com.solstitium.solstitium.model.dto.ResultadoConfiguracion;
import com.solstitium.solstitium.repository.ParametroRepository;
import jakarta.persistence.EntityManager;
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
class IndicadoresFinancierosTest {
    @Autowired private CalculadoraSombra sombra;
    @Autowired private CalculadoraTecnica tecnica;
    @Autowired private CalculadoraFinanciera financiera;
    @Autowired private ParametroRepository repositorio;
    @Autowired private JdbcTemplate consultas;
    @Autowired private EntityManager gestorEntidades;

    @ParameterizedTest
    @CsvSource({
            "1,150,40,1,1,1,central,6.40,550.8,Medianamente viable",
            "2,215,40,2,1,1,central,4.59,848.9,Viable",
            "3,250,40,3,1,2,central,3.81,1066.5,Viable",
            "4,300,40,2,2,3,central,4.47,878.1,Viable",
            "4,300,40,2,2,3,mlpe,4.50,870.2,Viable",
            "5,410,60,1,4,1,central,7.31,456.7,Medianamente viable",
            "5,410,60,1,4,1,mlpe,4.86,790.7,Viable",
            "6,500,8,1,1,1,central,2.47,1755.6,Viable",
            "4,300,5,1,1,1,central,2.94,1446.4,Viable",
            "1,100,40,4,4,4,mlpe,14.06,142.0,No viable"
    })
    void reproduceRecuperacionRoiYViabilidadDelErs(int estrato, String consumo, String area, int manana,
                                                int mediodia, int tarde, String codigo, String anios,
                                                String roi, String clasificacion) {
        var parametros = repositorio.obtenerParametros();
        var solicitud = SolicitudesPrueba.crear(estrato, consumo, area, manana, mediodia, tarde);
        var factor = sombra.calcularFactor(solicitud.getRespuestasSombra(), parametros);
        var configuracion = factor.getTramo().getConfiguraciones().stream()
                .filter(actual -> actual.getCodigo().equals(codigo)).findFirst().orElseThrow();
        var resultado = tecnica.dimensionar(solicitud, factor, configuracion, parametros);
        financiera.calcularIndicadores(resultado, solicitud, parametros);
        assertThat(resultado.getTiempoRecuperacion()).isCloseTo(new BigDecimal(anios), within(new BigDecimal("0.005")));
        assertThat(resultado.getRoi()).isCloseTo(new BigDecimal(roi), within(new BigDecimal("0.05")));
        assertThat(resultado.getClasificacion()).isEqualTo(clasificacion);
    }

    @ParameterizedTest
    @CsvSource({"7200,6,Viable", "7200.012,6.00001,Medianamente viable",
            "12000,10,Medianamente viable", "12000.012,10.00001,No viable"})
    void respetaLimitesDeViabilidadSinIncremento(String costo, String anios, String clasificacion) {
        consultas.update("update parametro set valor = 0 where clave = 'incremento_anual_tarifa'");
        gestorEntidades.clear();
        var resultado = resultado(new BigDecimal(costo), new BigDecimal("100"));
        financiera.calcularRentabilidad(resultado, repositorio.obtenerParametros());
        assertThat(resultado.getTiempoRecuperacion()).isEqualByComparingTo(anios);
        assertThat(resultado.getClasificacion()).isEqualTo(clasificacion);
    }

    @Test
    void ahorroCeroNoRecuperaYProduceRoiMenosCien() {
        var resultado = resultado(new BigDecimal("6600000"), BigDecimal.ZERO);
        financiera.calcularRentabilidad(resultado, repositorio.obtenerParametros());
        assertThat(resultado.getTiempoRecuperacion()).isNull();
        assertThat(resultado.getRoi()).isEqualByComparingTo("-100");
        assertThat(resultado.getClasificacion()).isEqualTo("No viable");
    }

    @Test
    void noExtiendeRecuperacionFueraDeLaVidaUtil() {
        consultas.update("update parametro set valor = 1 where clave = 'vida_util'");
        gestorEntidades.clear();
        var resultado = resultado(new BigDecimal("2400"), new BigDecimal("100"));
        financiera.calcularRentabilidad(resultado, repositorio.obtenerParametros());
        assertThat(resultado.getTiempoRecuperacion()).isNull();
        assertThat(resultado.getRoi()).isEqualByComparingTo("-50");
    }

    private ResultadoConfiguracion resultado(BigDecimal costo, BigDecimal ahorro) {
        var resultado = new ResultadoConfiguracion();
        resultado.setPaneles(1);
        resultado.setCostoTotal(costo);
        resultado.setAhorroMensual(ahorro);
        return resultado;
    }
}
