package com.solstitium.solstitium.model.dto;

import java.math.BigDecimal;

public record PanelCatalogo(Long id, String marca, String modelo, BigDecimal potenciaWp,
                           BigDecimal areaM2, boolean activo, boolean esReferencia) {}
