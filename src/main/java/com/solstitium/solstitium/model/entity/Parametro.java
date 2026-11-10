package com.solstitium.solstitium.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Parámetro escalar en formato clave-valor (HSP, costo unitario, vida útil, ...). */
@Entity
@Table(name = "parametro")
@Getter
@Setter
@NoArgsConstructor
public class Parametro {

    @Id
    @Column(name = "clave", length = 50)
    private String clave;

    @Column(name = "valor", nullable = false, precision = 14, scale = 4)
    private BigDecimal valor;

    @Column(name = "unidad", nullable = false, length = 20)
    private String unidad;

    @Column(name = "descripcion", nullable = false, length = 200)
    private String descripcion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
}
