package com.solstitium.solstitium.service.calculo;

import java.math.BigDecimal;
import java.math.MathContext;

/** Capacidad de representación compartida por administración y motor; no fija rangos de ingeniería. */
public final class LimitesCalculoFinanciero {
    private static final int MAXIMO_EXPONENTE = 999999999;

    private LimitesCalculoFinanciero() {}

    public static int validarHorizonte(BigDecimal vidaUtil, BigDecimal incrementoAnual) {
        int anios;
        try {
            anios = vidaUtil == null ? 0 : vidaUtil.intValueExact();
        } catch (ArithmeticException excepcion) {
            throw new IllegalArgumentException("La vida útil debe ser un número entero de años representable por el motor.", excepcion);
        }
        if (anios <= 0 || anios > MAXIMO_EXPONENTE) {
            throw new IllegalArgumentException("La vida útil debe estar entre 1 y " + MAXIMO_EXPONENTE
                    + " años por la capacidad de cálculo; también debe respetar sus límites configurados.");
        }
        if (incrementoAnual == null || incrementoAnual.signum() < 0) {
            throw new IllegalArgumentException("El incremento anual debe existir y no ser negativo.");
        }
        // Incluso un exponente admisible puede desbordar la escala con incrementos extremos.
        BigDecimal tasa = incrementoAnual.movePointLeft(2);
        try {
            if (tasa.signum() != 0) {
                BigDecimal.ONE.add(tasa).pow(anios, MathContext.DECIMAL128);
            }
        } catch (ArithmeticException excepcion) {
            throw new IllegalArgumentException("La vida útil y el incremento anual combinados exceden la capacidad de cálculo.", excepcion);
        }
        return anios;
    }
}
