package com.solstitium.solstitium.service;

import com.solstitium.solstitium.exception.ConflictoCatalogoException;
import com.solstitium.solstitium.exception.PanelNoEncontradoException;
import com.solstitium.solstitium.model.dto.*;
import com.solstitium.solstitium.model.entity.*;
import com.solstitium.solstitium.repository.AdministracionParametrosRepository;
import com.solstitium.solstitium.repository.AdministracionParametrosRepository.Datos;
import com.solstitium.solstitium.repository.BitacoraRepository;
import com.solstitium.solstitium.repository.PanelRepository;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** RF-07 / CU-13–CU-15: catálogo, coherencia y auditoría en la misma transacción. */
@Service
@RequiredArgsConstructor
public class PanelService {
    private final PanelRepository paneles;
    private final AdministracionParametrosRepository parametros;
    private final BitacoraRepository bitacora;
    private final AdministradorSesion sesion;
    private final ValidadorParametros validador;
    private final Validator validadorEstructura;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConsultaPaneles listar() {
        sesion.obtener();
        var datos = parametros.cargar(false);
        return new ConsultaPaneles(paneles.buscarTodos().stream().map(this::dto).toList(),
                datos.limites().stream().filter(l -> l.getTabla().equals("panel") && l.getClave() == null)
                        .map(l -> new ConsultaPaneles.Limite(l.getCampo(), l.getValorMinimo(), l.getValorMaximo())).toList());
    }

    @Transactional
    public PanelCatalogo registrar(SolicitudPanel solicitud) {
        var administrador = sesion.obtener();
        var datos = cargarBloqueados();
        validarPanel(solicitud, null, datos);
        var panel = new Panel();
        asignar(panel, solicitud);
        panel.setActivo(true);
        datos.paneles().add(panel);
        validarCoherencia(datos);
        paneles.guardar(panel);
        guardarConBitacora(panel, Map.of(), administrador);
        return dto(panel);
    }

    @Transactional
    public PanelCatalogo editar(Long id, SolicitudPanel solicitud) {
        var administrador = sesion.obtener();
        var datos = cargarBloqueados();
        var panel = buscar(id, datos);
        validarPanel(solicitud, id, datos);
        var anteriores = valores(panel);
        asignar(panel, solicitud);
        validarCoherencia(datos);
        guardarConBitacora(panel, anteriores, administrador);
        return dto(panel);
    }

    @Transactional
    public PanelCatalogo cambiarEstado(Long id, SolicitudEstadoPanel solicitud) {
        var administrador = sesion.obtener();
        validarEstructura(solicitud);
        var datos = cargarBloqueados();
        var panel = buscar(id, datos);
        if (panel.isEsReferencia() && !solicitud.activo()) {
            throw new ConflictoCatalogoException("Primero debe elegir otro panel de referencia antes de desactivar este panel.");
        }
        var anteriores = valores(panel);
        panel.setActivo(solicitud.activo());
        validarCoherencia(datos);
        guardarConBitacora(panel, anteriores, administrador);
        return dto(panel);
    }

    @Transactional
    public PanelCatalogo designarReferencia(Long id, SolicitudReferenciaPanel solicitud) {
        var administrador = sesion.obtener();
        validarEstructura(solicitud);
        var datos = cargarBloqueados();
        var nuevo = buscar(id, datos);
        if (!nuevo.isActivo()) throw new ConflictoCatalogoException("Primero debe activar el panel para elegirlo como referencia.");
        validarCoherencia(datos);
        if (nuevo.isEsReferencia()) return dto(nuevo);
        var anterior = datos.paneles().stream().filter(Panel::isEsReferencia).findFirst().orElseThrow();
        var valoresAnteriores = valores(anterior);
        var valoresNuevos = valores(nuevo);
        anterior.setEsReferencia(false);
        // El índice único parcial exige retirar y sincronizar antes de marcar la nueva referencia.
        parametros.sincronizar();
        nuevo.setEsReferencia(true);
        validarCoherencia(datos);
        guardarConBitacora(anterior, valoresAnteriores, administrador);
        guardarConBitacora(nuevo, valoresNuevos, administrador);
        return dto(nuevo);
    }

    private Datos cargarBloqueados() {
        // Mismo bloqueo y orden que ParametroService; se adquiere antes de leer paneles y límites.
        var datos = parametros.cargar(true);
        if (datos.parametros().isEmpty()) throw new IllegalStateException("Faltan los parámetros que coordinan la administración.");
        return datos;
    }

    private Panel buscar(Long id, Datos datos) {
        if (id == null || id <= 0) throw new IllegalArgumentException("El identificador del panel debe ser un entero positivo.");
        return datos.paneles().stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow(PanelNoEncontradoException::new);
    }

    private void validarEstructura(Object solicitud) {
        if (solicitud == null || !validadorEstructura.validate(solicitud).isEmpty()) {
            throw new IllegalArgumentException("Compruebe los campos obligatorios, sus tipos, longitud, precisión y confirmación.");
        }
    }

    private void validarPanel(SolicitudPanel solicitud, Long id, Datos datos) {
        validarEstructura(solicitud);
        if (solicitud.marca().strip().isBlank() || solicitud.modelo().strip().isBlank()) {
            throw new IllegalArgumentException("La marca y el modelo son obligatorios.");
        }
        validarLimite("potencia_wp", solicitud.potenciaWp(), datos);
        validarLimite("area_m2", solicitud.areaM2(), datos);
        if (datos.paneles().stream().anyMatch(p -> !Objects.equals(p.getId(), id)
                && normalizar(p.getMarca()).equals(normalizar(solicitud.marca()))
                && normalizar(p.getModelo()).equals(normalizar(solicitud.modelo())))) {
            throw new ConflictoCatalogoException("Ya existe un panel con la misma marca y modelo.");
        }
    }

    private void validarLimite(String campo, BigDecimal numero, Datos datos) {
        var limite = datos.limites().stream().filter(l -> l.getTabla().equals("panel")
                && l.getCampo().equals(campo) && l.getClave() == null).findFirst()
                .orElseThrow(() -> new IllegalStateException("Falta el límite del panel: " + campo + "."));
        if (numero.compareTo(limite.getValorMinimo()) < 0 || numero.compareTo(limite.getValorMaximo()) > 0) {
            throw new IllegalArgumentException("El valor de panel." + campo + " debe estar entre "
                    + limite.getValorMinimo().toPlainString() + " y " + limite.getValorMaximo().toPlainString() + ".");
        }
    }

    private void validarCoherencia(Datos datos) {
        validador.validarCoherencia(new CatalogoParametros(datos), datos);
    }

    private String normalizar(String texto) { return texto.strip().toLowerCase(Locale.ROOT); }

    private void asignar(Panel panel, SolicitudPanel solicitud) {
        panel.setMarca(solicitud.marca().strip());
        panel.setModelo(solicitud.modelo().strip());
        panel.setPotenciaWp(solicitud.potenciaWp());
        panel.setAreaM2(solicitud.areaM2());
    }

    private Map<String, String> valores(Panel panel) {
        var valores = new LinkedHashMap<String, String>();
        valores.put("marca", panel.getMarca());
        valores.put("modelo", panel.getModelo());
        valores.put("potencia_wp", panel.getPotenciaWp().stripTrailingZeros().toPlainString());
        valores.put("area_m2", panel.getAreaM2().stripTrailingZeros().toPlainString());
        valores.put("activo", String.valueOf(panel.isActivo()));
        valores.put("es_referencia", String.valueOf(panel.isEsReferencia()));
        return valores;
    }

    private void guardarConBitacora(Panel panel, Map<String, String> anteriores, Administrador administrador) {
        var registros = new ArrayList<CambioBitacora>();
        var fecha = LocalDateTime.now();
        valores(panel).forEach((campo, nuevo) -> {
            if (!Objects.equals(anteriores.get(campo), nuevo)) {
                var registro = new CambioBitacora();
                registro.setAdministrador(administrador);
                registro.setFecha(fecha);
                registro.setTablaAfectada("panel");
                registro.setRegistroId(panel.getId().toString());
                registro.setCampo(campo);
                registro.setValorAnterior(anteriores.getOrDefault(campo, "(sin registro)"));
                registro.setValorNuevo(nuevo);
                registros.add(registro);
            }
        });
        parametros.sincronizar();
        bitacora.saveAllAndFlush(registros);
    }

    private PanelCatalogo dto(Panel panel) {
        return new PanelCatalogo(panel.getId(), panel.getMarca(), panel.getModelo(), panel.getPotenciaWp(),
                panel.getAreaM2(), panel.isActivo(), panel.isEsReferencia());
    }
}
