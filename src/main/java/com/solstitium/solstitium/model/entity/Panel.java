package com.solstitium.solstitium.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Panel del catálogo. No tiene precio y nunca se elimina: se activa o desactiva. */
@Entity
@Table(name = "panel")
@Getter
@Setter
@NoArgsConstructor
public class Panel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.INTEGER) // la columna es serial
    @Column(name = "id")
    private Long id;

    @Column(name = "marca", nullable = false, length = 60)
    private String marca;

    @Column(name = "modelo", nullable = false, length = 60)
    private String modelo;

    @Column(name = "potencia_wp", nullable = false, precision = 7, scale = 2)
    private BigDecimal potenciaWp;

    @Column(name = "area_m2", nullable = false, precision = 5, scale = 3)
    private BigDecimal areaM2;

    /** Solo un panel activo puede ser el de referencia. */
    @Column(name = "es_referencia", nullable = false)
    private boolean esReferencia;

    @Column(name = "activo", nullable = false)
    private boolean activo;
}
