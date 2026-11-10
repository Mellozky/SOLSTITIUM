package com.solstitium.solstitium.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Estrato socioeconómico (1 a 6) con el ajuste que se aplica a su tarifa. */
@Entity
@Table(name = "estrato")
@Getter
@Setter
@NoArgsConstructor
public class Estrato {

    @Id
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "numero")
    private int numero;

    /**
     * Fijo por estrato: el administrador solo edita el porcentaje.
     * La columna es del tipo enumerado de PostgreSQL, por eso el valor se convierte al escribir.
     */
    @Convert(converter = TipoAjusteConverter.class)
    @Column(name = "tipo_ajuste", nullable = false, columnDefinition = "tipo_ajuste_estrato")
    @ColumnTransformer(write = "?::tipo_ajuste_estrato")
    private TipoAjuste tipoAjuste;

    @Column(name = "porcentaje_ajuste", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeAjuste;
}
