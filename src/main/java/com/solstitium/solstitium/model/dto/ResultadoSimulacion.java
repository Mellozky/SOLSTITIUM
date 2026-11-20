package com.solstitium.solstitium.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Respuesta de la figura 5 del ERS; no expone entidades JPA ni se persiste. */
@Getter
@Setter
@NoArgsConstructor
public class ResultadoSimulacion {
    private String tramo;
    private BigDecimal factorSombra;
    private boolean sinEspacio;
    private List<ResultadoConfiguracion> resultados = List.of();
    private String recomendada;
    private String motivoRecomendacion;
    private ResultadoConfiguracion escenarioSinSombra;
    private List<String> advertencias = List.of();
    private String panelReferencia;
    private LocalDateTime fechaTarifa;
    private BigDecimal gastoMensualSinSistema;
}
