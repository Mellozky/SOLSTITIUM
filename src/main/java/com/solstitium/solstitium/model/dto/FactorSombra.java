package com.solstitium.solstitium.model.dto;

import com.solstitium.solstitium.model.entity.TramoSombra;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Resultado interno del sombreado (figura 5 del ERS), sin persistir una simulación. */
@Getter
@RequiredArgsConstructor
public class FactorSombra {

    private final BigDecimal perdida;
    private final BigDecimal factor;
    private final TramoSombra tramo;
}
