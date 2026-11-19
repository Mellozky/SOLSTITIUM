package com.solstitium.solstitium.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.entity.Configuracion;
import com.solstitium.solstitium.model.entity.Estrato;
import com.solstitium.solstitium.model.entity.FranjaHoraria;
import com.solstitium.solstitium.model.entity.LimiteParametro;
import com.solstitium.solstitium.model.entity.TipoAjuste;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifica los datos de V2 contra la tabla 30 del ERS, sin insertar una copia desde el test. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DatosInicialesTest {

    @Autowired
    private ParametroRepository repositorio;

    @Autowired
    private PanelRepository paneles;

    @Autowired
    private JdbcTemplate consultas;

    @Test
    void cargaLosQuinceParametrosSupuestosDelErs() {
        Map<String, String> esperados = Map.ofEntries(
                Map.entry("costo_unitario_kwh", "1000"), Map.entry("consumo_subsistencia", "173"),
                Map.entry("incremento_anual_tarifa", "5"), Map.entry("hsp", "4.5"),
                Map.entry("factor_rendimiento", "0.80"), Map.entry("factor_aprovechamiento", "0.75"),
                Map.entry("relacion_cc_ca", "1.2"), Map.entry("vida_util", "25"),
                Map.entry("tope_sobredimensionamiento", "25"), Map.entry("amplificacion_serie", "3"),
                Map.entry("consumo_maximo", "2000"), Map.entry("umbral_advertencia_consumo", "800"),
                Map.entry("area_minima", "5"), Map.entry("area_maxima", "500"),
                Map.entry("umbral_diferencia_factura", "20"));
        Map<String, BigDecimal> actuales = repositorio.obtenerParametros().getParametros();

        assertThat(actuales).containsOnlyKeys(esperados.keySet());
        esperados.forEach((clave, valor) -> assertThat(actuales.get(clave)).isEqualByComparingTo(valor));
    }

    @Test
    void cargaLosSeisEstratosConSuAjuste() {
        var estratos = repositorio.obtenerParametros().getEstratos();

        assertThat(estratos).extracting(Estrato::getNumero).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(estratos).extracting(Estrato::getTipoAjuste).containsExactly(
                TipoAjuste.SUBSIDIO, TipoAjuste.SUBSIDIO, TipoAjuste.SUBSIDIO,
                TipoAjuste.NINGUNO, TipoAjuste.CONTRIBUCION, TipoAjuste.CONTRIBUCION);
        String[] porcentajes = {"50", "40", "15", "0", "20", "20"};
        for (int indice = 0; indice < porcentajes.length; indice++) {
            assertThat(estratos.get(indice).getPorcentajeAjuste()).isEqualByComparingTo(porcentajes[indice]);
        }
    }

    @Test
    void conservaLasFranjasPorcionesYConfiguracionesDeSombra() {
        ConjuntoParametros conjunto = repositorio.obtenerParametros();

        assertThat(conjunto.getFranjas()).extracting(FranjaHoraria::getHoraInicio)
                .containsExactly(LocalTime.of(6, 0), LocalTime.of(10, 0), LocalTime.of(14, 0));
        assertThat(conjunto.getFranjas()).extracting(FranjaHoraria::getHoraFin)
                .containsExactly(LocalTime.of(10, 0), LocalTime.of(14, 0), LocalTime.of(18, 0));
        assertThat(conjunto.getFranjas().stream().map(FranjaHoraria::getPeso)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1");
        String[] pesos = {"0.15", "0.70", "0.15"};
        for (int indice = 0; indice < pesos.length; indice++) {
            assertThat(conjunto.getFranjas().get(indice).getPeso()).isEqualByComparingTo(pesos[indice]);
        }
        String[] porciones = {"0", "0.10", "0.25", "0.50"};
        assertThat(conjunto.getPorciones()).hasSize(4);
        for (int indice = 0; indice < porciones.length; indice++) {
            assertThat(conjunto.getPorciones().get(indice).getValor()).isEqualByComparingTo(porciones[indice]);
        }
        assertThat(conjunto.getTramos()).hasSize(5);
        String[] minimos = {"1", "0.90", "0.75", "0.55", "0"};
        String[] maximos = {"1", "1", "0.90", "0.75", "0.55"};
        for (int indice = 0; indice < minimos.length; indice++) {
            var tramo = conjunto.getTramos().get(indice);
            assertThat(tramo.getFactorMinimo()).isEqualByComparingTo(minimos[indice]);
            assertThat(tramo.getFactorMaximo()).isEqualByComparingTo(maximos[indice]);
            assertThat(tramo.isMuestraAdvertencia()).isEqualTo(indice == 4);
            assertThat(tramo.getConfiguraciones()).extracting(Configuracion::getCodigo)
                    .containsExactly(indice < 3 ? new String[]{"central"} : new String[]{"central", "mlpe"});
        }
    }

    @Test
    void conservaLosPanelesCostosYRecuperacionDelErs() {
        ConjuntoParametros conjunto = repositorio.obtenerParametros();
        var referencia = conjunto.getPanelReferencia();

        assertThat(referencia.isActivo()).isTrue();
        assertThat(referencia.getPotenciaWp()).isEqualByComparingTo("550");
        assertThat(referencia.getAreaM2()).isEqualByComparingTo("2.6");
        assertThat(paneles.buscarTodos()).hasSize(2).filteredOn(panel -> !panel.isEsReferencia())
                .singleElement().satisfies(panel -> {
                    assertThat(panel.getPotenciaWp()).isEqualByComparingTo("450");
                    assertThat(panel.getAreaM2()).isEqualByComparingTo("2.1");
                });
        assertThat(conjunto.getConfiguraciones()).hasSize(2);
        assertThat(conjunto.getConfiguraciones().get(0).getCostoKwp()).isEqualByComparingTo("4000000");
        assertThat(conjunto.getConfiguraciones().get(0).getCoeficienteRecuperacion()).isEqualByComparingTo("0");
        assertThat(conjunto.getConfiguraciones().get(1).getCostoKwp()).isEqualByComparingTo("4600000");
        assertThat(conjunto.getConfiguraciones().get(1).getCoeficienteRecuperacion()).isEqualByComparingTo("0.36");
    }

    @Test
    void cargaRangosAbiertosYLimitesParaCadaParametroGeneral() {
        ConjuntoParametros conjunto = repositorio.obtenerParametros();

        assertThat(conjunto.getRangos()).hasSize(3);
        assertThat(conjunto.getRangos().get(0).getAniosMinimo()).isEqualByComparingTo("0");
        assertThat(conjunto.getRangos().get(0).getAniosMaximo()).isEqualByComparingTo("6");
        assertThat(conjunto.getRangos().get(1).getAniosMinimo()).isEqualByComparingTo("6");
        assertThat(conjunto.getRangos().get(1).getAniosMaximo()).isEqualByComparingTo("10");
        assertThat(conjunto.getRangos().get(2).getAniosMinimo()).isEqualByComparingTo("10");
        assertThat(conjunto.getRangos().get(2).getAniosMaximo()).isNull();
        var limitesGenerales = conjunto.getLimites().stream()
                .filter(limite -> limite.getTabla().equals("parametro")).toList();
        assertThat(limitesGenerales).extracting(LimiteParametro::getClave)
                .containsExactlyInAnyOrderElementsOf(conjunto.getParametros().keySet());
        for (var limite : limitesGenerales) {
            assertThat(conjunto.getParametros().get(limite.getClave()))
                    .isBetween(limite.getValorMinimo(), limite.getValorMaximo());
        }
        assertThat(limitesGenerales).filteredOn(limite -> limite.getClave().equals("costo_unitario_kwh"))
                .singleElement().satisfies(limite -> {
                    assertThat(limite.getValorMinimo()).isEqualByComparingTo("500");
                    assertThat(limite.getValorMaximo()).isEqualByComparingTo("3000");
                });
    }

    @Test
    void flywayRegistraV2SinCrearCredencialesCompartidas() {
        assertThat(consultas.queryForObject(
                "select count(*) from flyway_schema_history where version = '2' and success", Long.class))
                .isEqualTo(1L);
        assertThat(consultas.queryForObject("select count(*) from administrador", Long.class)).isZero();
        assertThat(consultas.queryForObject("select count(*) from bitacora_parametro", Long.class)).isZero();
    }
}
