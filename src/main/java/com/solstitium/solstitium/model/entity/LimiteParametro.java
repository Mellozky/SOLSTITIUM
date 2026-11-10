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

/**
 * Mínimo y máximo permitido de un parámetro. Se carga por script y el módulo de
 * administración solo lo consulta. {@code clave} es nula cuando el límite aplica a toda la columna.
 */
@Entity
@Table(name = "limite_parametro")
@Getter
@Setter
@NoArgsConstructor
public class LimiteParametro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.INTEGER) // la columna es serial
    @Column(name = "id")
    private Long id;

    @Column(name = "tabla", nullable = false, length = 50)
    private String tabla;

    @Column(name = "campo", nullable = false, length = 50)
    private String campo;

    @Column(name = "clave", length = 50)
    private String clave;

    @Column(name = "valor_minimo", nullable = false, precision = 14, scale = 4)
    private BigDecimal valorMinimo;

    @Column(name = "valor_maximo", nullable = false, precision = 14, scale = 4)
    private BigDecimal valorMaximo;
}
