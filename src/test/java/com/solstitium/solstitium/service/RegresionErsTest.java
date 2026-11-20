package com.solstitium.solstitium.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.solstitium.solstitium.apoyo.SolicitudesPrueba;
import com.solstitium.solstitium.repository.ParametroRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Tablas 32–34 del ERS: quince resultados de los once escenarios. */
@SpringBootTest
@Transactional
class RegresionErsTest {
    @Autowired private SimulacionService servicio;
    @Autowired private JdbcTemplate consultas;
    @Autowired private ParametroRepository repositorio;

    @ParameterizedTest
    @CsvSource({
            "1,1,160|150|140,40,1,1,1,central,3,1.65,1.5,6600000,75000,100,6.40,550.8,Medianamente viable,central",
            "2,2,220|200|210|230,40,2,1,1,central,4,2.20,2,8800000,145800,100,4.59,848.9,Viable,central",
            "3,3,250,40,3,1,2,central,5,2.75,2.5,11000000,224050,100,3.81,1066.5,Viable,central",
            "4,4,300|320|280,40,2,2,3,central,7,3.85,3.5,15400000,262994,87.7,4.47,878.1,Viable,central",
            "4,4,300|320|280,40,2,2,3,mlpe,7,3.85,3.5,17710000,300000,100,4.50,870.2,Viable,central",
            "5,5,400|420,60,1,4,1,central,9,4.95,4.5,19800000,192456,39.1,7.31,456.7,Medianamente viable,mlpe",
            "5,5,400|420,60,1,4,1,mlpe,9,4.95,4.5,22770000,354119,72.0,4.86,790.7,Viable,mlpe",
            "6,6,500|520|480,8,1,1,1,central,2,1.10,1,4400000,142560,23.8,2.47,1755.6,Viable,central",
            "7,4,300,5,1,1,1,central,1,0.55,0.5,2200000,59400,19.8,2.94,1446.4,Viable,central",
            "8,1,100,40,4,4,4,central,3,1.65,1.5,6600000,0,0,NO,-100,No viable,mlpe",
            "8,1,100,40,4,4,4,mlpe,3,1.65,1.5,7590000,32076,64.2,14.06,142.0,No viable,mlpe",
            "9,4,300|320|280,40,2,2,3,central,8,3.60,3,14400000,245916,82.0,4.47,878.1,Viable,mlpe",
            "9,4,300|320|280,40,2,2,3,mlpe,8,3.60,3,16560000,297354,99.1,4.27,928.4,Viable,mlpe",
            "10,5,850|900,6,1,1,1,central,1,0.55,0.5,2200000,71280,6.8,2.47,1755.6,Viable,central",
            "11,1,160|150|140,40,1,1,1,central,3,1.65,1.5,6600000,90000,100,5.46,681.0,Viable,central"
    })
    void verificaElResultadoCompletoDeCadaConfiguracion(int caso, int estrato, String consumos, String area,
            int manana, int mediodia, int tarde, String codigo, int paneles, String potencia, String inversor,
            String costo, String ahorro, String cobertura, String anios, String roi, String clasificacion, String recomendada) {
        if (caso == 9) {
            consultas.update("update panel set es_referencia = false where es_referencia");
            consultas.update("update panel set es_referencia = true where modelo = 'Alterno ERS 450'");
        }
        if (caso == 11) {
            consultas.update("update parametro set valor = 1200 where clave = 'costo_unitario_kwh'");
        }
        var solicitud = SolicitudesPrueba.crear(estrato, consumos, area, manana, mediodia, tarde);
        if (caso == 3) solicitud.setValorFactura(new BigDecimal("230000"));
        if (caso == 10) solicitud.setValorFactura(new BigDecimal("600000"));
        var respuesta = servicio.simular(solicitud);
        var resultado = respuesta.getResultados().stream().filter(actual -> actual.getConfiguracion().equals(codigo))
                .findFirst().orElseThrow();
        assertThat(resultado.getPaneles()).isEqualTo(paneles);
        assertThat(resultado.getPotenciaKwp()).isEqualByComparingTo(potencia);
        assertThat(resultado.getInversorKw()).isEqualByComparingTo(inversor);
        assertThat(resultado.getCostoTotal()).isEqualByComparingTo(costo);
        assertThat(resultado.getAhorroMensual()).isCloseTo(new BigDecimal(ahorro), within(new BigDecimal("0.5")));
        assertThat(resultado.getCobertura()).isCloseTo(new BigDecimal(cobertura), within(new BigDecimal("0.05")));
        assertThat(resultado.getRoi()).isCloseTo(new BigDecimal(roi), within(new BigDecimal("0.05")));
        assertThat(resultado.getClasificacion()).isEqualTo(clasificacion);
        assertThat(respuesta.getRecomendada()).isEqualTo(recomendada);
        if (anios.equals("NO")) {
            assertThat(resultado.getTiempoRecuperacion()).isNull();
        } else {
            assertThat(resultado.getTiempoRecuperacion()).isCloseTo(new BigDecimal(anios), within(new BigDecimal("0.005")));
        }
        if (caso == 5 || caso == 8) {
            assertThat(respuesta.getAdvertencias()).anyMatch(aviso -> aviso.contains("Baja confianza"));
        }
        if (caso == 10) {
            assertThat(respuesta.getAdvertencias()).anyMatch(aviso -> aviso.contains("1080000.00"));
            assertThat(respuesta.getAdvertencias()).anyMatch(aviso -> aviso.contains("Consumo poco común"));
        }
    }

    @Test
    void unaSimulacionNoModificaNingunaTabla() {
        Map<String, Long> antes = filas();
        servicio.simular(SolicitudesPrueba.crear(4, "300", "40", 2, 2, 3));
        repositorio.flush();
        assertThat(filas()).isEqualTo(antes);
    }

    @Test
    void enEmpateExactoConservaElOrdenYLoExplica() {
        consultas.update("update configuracion set costo_kwp = 4000000, coeficiente_recuperacion = 0 where codigo = 'mlpe'");
        var respuesta = servicio.simular(SolicitudesPrueba.crear(4, "300", "40", 2, 2, 3));
        assertThat(respuesta.getRecomendada()).isEqualTo("central");
        assertThat(respuesta.getMotivoRecomendacion()).contains("mismo tiempo");
        assertThat(respuesta.getResultados().get(0).getTiempoRecuperacion())
                .isEqualByComparingTo(respuesta.getResultados().get(1).getTiempoRecuperacion());
    }

    private Map<String, Long> filas() {
        Map<String, Long> conteos = new LinkedHashMap<>();
        for (String tabla : List.of("administrador", "bitacora_parametro", "parametro", "limite_parametro",
                "estrato", "franja_horaria", "porcion_afectada", "configuracion", "tramo_sombreado",
                "tramo_configuracion", "panel", "rango_viabilidad")) {
            conteos.put(tabla, consultas.queryForObject("select count(*) from " + tabla, Long.class));
        }
        return conteos;
    }
}
