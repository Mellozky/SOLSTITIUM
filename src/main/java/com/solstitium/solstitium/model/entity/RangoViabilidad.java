package com.solstitium.solstitium.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Clasificación de viabilidad según los años de recuperación de la inversión. */
@Entity
@Table(name = "rango_viabilidad")
@Getter
@Setter
@NoArgsConstructor
public class RangoViabilidad {

    @Id
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "id")
    private Long id;

    @Column(name = "clasificacion", nullable = false, length = 30)
    private String clasificacion;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "orden", nullable = false)
    private int orden;

    @Column(name = "anios_minimo", nullable = false, precision = 5, scale = 2)
    private BigDecimal aniosMinimo;

    /** Nulo en el último rango (sin tope). */
    @Column(name = "anios_maximo", precision = 5, scale = 2)
    private BigDecimal aniosMaximo;
}
