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

/** Configuración de instalación: inversor central o MLPE. */
@Entity
@Table(name = "configuracion")
@Getter
@Setter
@NoArgsConstructor
public class Configuracion {

    @Id
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "id")
    private Long id;

    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @Column(name = "costo_kwp", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoKwp;

    @Column(name = "coeficiente_recuperacion", nullable = false, precision = 4, scale = 3)
    private BigDecimal coeficienteRecuperacion;
}
