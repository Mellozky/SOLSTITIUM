package com.solstitium.solstitium.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro de la bitácora: un valor cambiado por un administrador.
 * Identifica el dato por tabla, registro y campo, sin llave foránea a las tablas de parámetros.
 */
@Entity
@Table(name = "bitacora_parametro")
@Getter
@Setter
@NoArgsConstructor
public class CambioBitacora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "administrador_id", nullable = false)
    private Administrador administrador;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "tabla_afectada", nullable = false, length = 50)
    private String tablaAfectada;

    @Column(name = "registro_id", nullable = false, length = 50)
    private String registroId;

    @Column(name = "campo", nullable = false, length = 50)
    private String campo;

    @Column(name = "valor_anterior", nullable = false, columnDefinition = "text")
    private String valorAnterior;

    @Column(name = "valor_nuevo", nullable = false, columnDefinition = "text")
    private String valorNuevo;
}
