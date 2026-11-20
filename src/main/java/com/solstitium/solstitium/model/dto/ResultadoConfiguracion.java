package com.solstitium.solstitium.model.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resultado transitorio por configuración (ERS, figura 5). No es una entidad JPA. */
@Getter
@Setter
@NoArgsConstructor
public class ResultadoConfiguracion {

    private String configuracion;
    private int paneles;
    private BigDecimal potenciaKwp;
    private BigDecimal inversorKw;
    private BigDecimal costoTotal;

    // Los completa CalculadoraFinanciera; antes de esa etapa null no representa ahorro cero.
    private BigDecimal energiaMensual;
    private BigDecimal ahorroMensual;
    private BigDecimal gastoMensualConSistema;
    private BigDecimal cobertura;
    private BigDecimal tiempoRecuperacion;
    private BigDecimal roi;
    private String clasificacion;
}
