package com.solstitium.solstitium.service.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LimitesCalculoFinancieroTest {
    @ParameterizedTest
    @CsvSource({"1,0", "25,5", "999999999,0", "100,100"})
    void aceptaHorizontesRepresentables(String vida, String incremento) {
        assertThat(LimitesCalculoFinanciero.validarHorizonte(new BigDecimal(vida), new BigDecimal(incremento)))
                .isEqualTo(Integer.parseInt(vida));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "25.5", "1000000000", "2147483647", "2147483648"})
    void rechazaVidaNoRepresentable(String vida) {
        assertThatThrownBy(() -> LimitesCalculoFinanciero.validarHorizonte(new BigDecimal(vida), new BigDecimal("5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "9000"})
    void rechazaCrecimientoQueExpandiriaElAcumulado(String incremento) {
        assertThatThrownBy(() -> LimitesCalculoFinanciero.validarHorizonte(
                new BigDecimal("999999999"), new BigDecimal(incremento)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100 dígitos");
    }

    @Test
    void rechazaDesbordamientoDeLaCombinacion() {
        assertThatThrownBy(() -> LimitesCalculoFinanciero.validarHorizonte(new BigDecimal("999999999"), new BigDecimal("9999999999")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("combinados");
    }
    @Test
    void respetaFronteraDeMagnitudSinExpandirLaPotencia() {
        assertThat(LimitesCalculoFinanciero.validarHorizonte(new BigDecimal("99"), new BigDecimal("900"))).isEqualTo(99);
        assertThatThrownBy(() -> LimitesCalculoFinanciero.validarHorizonte(new BigDecimal("100"), new BigDecimal("900")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
