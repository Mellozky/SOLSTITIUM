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

/** Porción del techo afectada por la sombra (nada, borde o esquina, ...) y su valor numérico. */
@Entity
@Table(name = "porcion_afectada")
@Getter
@Setter
@NoArgsConstructor
public class PorcionAfectada {

    @Id
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "id")
    private Long id;

    @Column(name = "etiqueta", nullable = false, length = 50)
    private String etiqueta;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "orden", nullable = false)
    private int orden;

    @Column(name = "valor", nullable = false, precision = 4, scale = 3)
    private BigDecimal valor;
}
