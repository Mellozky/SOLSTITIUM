package com.solstitium.solstitium.service.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.solstitium.solstitium.apoyo.SolicitudesPrueba;
import com.solstitium.solstitium.repository.ParametroRepository;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class GeneracionAhorroTest {
    @Autowired private CalculadoraSombra sombra;
    @Autowired private CalculadoraTecnica tecnica;
    @Autowired private CalculadoraFinanciera financiera;
    @Autowired private ParametroRepository repositorio;

    @ParameterizedTest
    @CsvSource({
            "1,160|150|140,40,1,1,1,central,75000,100",
            "2,220|200|210|230,40,2,1,1,central,145800,100",
            "3,250,40,3,1,2,central,224050,100",
            "4,300|320|280,40,2,2,3,central,262994,87.7",
            "4,300|320|280,40,2,2,3,mlpe,300000,100",
            "5,400|420,60,1,4,1,central,192456,39.1",
            "5,400|420,60,1,4,1,mlpe,354119,72.0",
            "6,500|520|480,8,1,1,1,central,142560,23.8",
            "4,300,5,1,1,1,central,59400,19.8",
            "1,100,40,4,4,4,central,0,0",
            "1,100,40,4,4,4,mlpe,32076,64.2"
    })
    void reproduceAhorroYCoberturaDelErs(int estrato, String consumos, String area, int manana, int mediodia,
                                       int tarde, String codigo, String ahorro, String cobertura) {
        var parametros = repositorio.obtenerParametros();
        var solicitud = SolicitudesPrueba.crear(estrato, consumos, area, manana, mediodia, tarde);
        var factor = sombra.calcularFactor(solicitud.getRespuestasSombra(), parametros);
        var configuracion = factor.getTramo().getConfiguraciones().stream()
                .filter(actual -> actual.getCodigo().equals(codigo)).findFirst().orElseThrow();
        var resultado = tecnica.dimensionar(solicitud, factor, configuracion, parametros);
        financiera.calcularGeneracionYAhorro(resultado, solicitud, parametros);
        assertThat(resultado.getAhorroMensual()).isCloseTo(new BigDecimal(ahorro), within(new BigDecimal("0.50")));
        assertThat(resultado.getCobertura()).isCloseTo(new BigDecimal(cobertura), within(new BigDecimal("0.05")));
        assertThat(resultado.getEnergiaMensual()).isBetween(BigDecimal.ZERO, solicitud.consumoPromedio());
        assertThat(resultado.getTiempoRecuperacion()).isNull();
    }
}
