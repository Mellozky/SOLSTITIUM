package com.solstitium.solstitium.service;

import com.solstitium.solstitium.model.dto.CambioParametro;
import com.solstitium.solstitium.model.dto.ConsultaParametros;
import com.solstitium.solstitium.model.dto.SolicitudActualizacionParametros;
import com.solstitium.solstitium.model.entity.CambioBitacora;
import com.solstitium.solstitium.repository.AdministracionParametrosRepository;
import com.solstitium.solstitium.repository.BitacoraRepository;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** CU-08–CU-12/CU-15: modificación atómica de registros existentes y auditoría por valor. */
@Service
@RequiredArgsConstructor
public class ParametroService {
    private final AdministracionParametrosRepository repositorio;
    private final BitacoraRepository bitacora;
    private final AdministradorSesion sesion;
    private final ValidadorParametros validador;
    private final Validator validadorEstructura;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConsultaParametros consultar(String seccion) {
        sesion.obtener();
        if (seccion == null || !Set.of("tarifas", "tecnicos", "sombreado", "costos", "rangos").contains(seccion)) {
            throw new IllegalArgumentException("Seleccione tarifas, tecnicos, sombreado, costos o rangos.");
        }
        var datos = repositorio.cargar(false);
        var registros = new CatalogoParametros(datos).registros.stream().filter(r -> r.secciones.contains(seccion)).toList();
        var limites = datos.limites().stream().filter(l -> registros.stream().anyMatch(r -> r.tabla.equals(l.getTabla())
                && (l.getClave() == null || l.getClave().equals(r.id))))
                .map(l -> new ConsultaParametros.Limite(l.getTabla(), l.getCampo(), l.getClave(), l.getValorMinimo(), l.getValorMaximo())).toList();
        return new ConsultaParametros(seccion, registros.stream().map(CatalogoParametros.Registro::dto).toList(), limites);
    }

    @Transactional
    public int actualizar(SolicitudActualizacionParametros solicitud) {
        var administrador = sesion.obtener();
        if (solicitud == null || !validadorEstructura.validate(solicitud).isEmpty()) {
            throw new IllegalArgumentException("Debe enviar una lista de cambios con tabla, campo, registroId y valorNuevo válidos.");
        }
        var datos = repositorio.cargar(true);
        var catalogo = new CatalogoParametros(datos);
        var preparados = new ArrayList<CambioPreparado>();
        var destinos = new HashSet<List<String>>();
        // Ninguna entidad se modifica hasta terminar ambas fases de validación.
        for (CambioParametro cambio : solicitud.cambios()) {
            if (!destinos.add(List.of(cambio.tabla(), cambio.registroId(), cambio.campo()))) {
                throw new IllegalArgumentException("Un mismo valor no puede aparecer dos veces en el lote.");
            }
            var registro = catalogo.buscar(cambio.tabla(), cambio.registroId());
            Object nuevo = validador.validarValor(cambio, registro, datos);
            Object anterior = registro.valores.get(cambio.campo());
            if (!iguales(anterior, nuevo)) preparados.add(new CambioPreparado(registro, cambio.campo(), anterior, nuevo));
        }
        preparados.forEach(c -> c.registro.valores.put(c.campo, c.nuevo));
        validador.validarCoherencia(catalogo, datos);

        LocalDateTime fecha = LocalDateTime.now();
        List<CambioBitacora> registrosBitacora = new ArrayList<>();
        for (var cambio : preparados) {
            cambio.registro.campos.get(cambio.campo).guardar().accept(cambio.nuevo);
            if (cambio.registro.tabla.equals("parametro")) {
                datos.parametros().stream().filter(p -> p.getClave().equals(cambio.registro.id)).findFirst().orElseThrow()
                        .setFechaActualizacion(fecha);
            }
            var registro = new CambioBitacora();
            registro.setAdministrador(administrador);
            registro.setFecha(fecha);
            registro.setTablaAfectada(cambio.registro.tabla);
            registro.setRegistroId(cambio.registro.id);
            registro.setCampo(cambio.campo);
            registro.setValorAnterior(texto(cambio.anterior));
            registro.setValorNuevo(texto(cambio.nuevo));
            registrosBitacora.add(registro);
        }
        // Si falla la auditoría, la transacción revierte también estos parámetros ya sincronizados.
        repositorio.sincronizar();
        bitacora.saveAllAndFlush(registrosBitacora);
        return preparados.size();
    }

    private boolean iguales(Object anterior, Object nuevo) {
        return anterior instanceof BigDecimal numero && nuevo instanceof BigDecimal otro
                ? numero.compareTo(otro) == 0 : Objects.equals(anterior, nuevo);
    }

    private String texto(Object valor) {
        return valor instanceof BigDecimal numero ? numero.stripTrailingZeros().toPlainString() : String.valueOf(valor);
    }

    private record CambioPreparado(CatalogoParametros.Registro registro, String campo, Object anterior, Object nuevo) {}
}
