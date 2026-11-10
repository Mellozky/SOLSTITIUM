package com.solstitium.solstitium.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Tramo del factor de sombreado (sin sombra, leve, moderada, alta, severa).
 * La tabla intermedia {@code tramo_configuracion} queda representada por la lista de configuraciones.
 */
@Entity
@Table(name = "tramo_sombreado")
@Getter
@Setter
@NoArgsConstructor
public class TramoSombra {

    @Id
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "id")
    private Long id;

    @Column(name = "nombre", nullable = false, length = 30)
    private String nombre;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "orden", nullable = false)
    private int orden;

    @Column(name = "factor_minimo", nullable = false, precision = 4, scale = 3)
    private BigDecimal factorMinimo;

    @Column(name = "factor_maximo", nullable = false, precision = 4, scale = 3)
    private BigDecimal factorMaximo;

    @Column(name = "muestra_advertencia", nullable = false)
    private boolean muestraAdvertencia;

    /** Configuraciones que se evalúan en este tramo (una o dos). */
    @ManyToMany
    @JoinTable(
            name = "tramo_configuracion",
            joinColumns = @JoinColumn(name = "tramo_id"),
            inverseJoinColumns = @JoinColumn(name = "configuracion_id"))
    @OrderBy("id")
    private List<Configuracion> configuraciones = new ArrayList<>();
}
