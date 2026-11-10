package com.solstitium.solstitium.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Franja del día (mañana, mediodía, tarde) con su peso en el cálculo del sombreado. */
@Entity
@Table(name = "franja_horaria")
@Getter
@Setter
@NoArgsConstructor
public class FranjaHoraria {

    @Id
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "id")
    private Long id;

    @Column(name = "nombre", nullable = false, length = 20)
    private String nombre;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "orden", nullable = false)
    private int orden;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "peso", nullable = false, precision = 4, scale = 3)
    private BigDecimal peso;
}
