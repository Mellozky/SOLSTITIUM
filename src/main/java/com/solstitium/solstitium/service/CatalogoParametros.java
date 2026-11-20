package com.solstitium.solstitium.service;

import com.solstitium.solstitium.model.dto.ConsultaParametros;
import com.solstitium.solstitium.repository.AdministracionParametrosRepository.Datos;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Lista cerrada de destinos y conversiones, independiente de los nombres recibidos por HTTP. */
final class CatalogoParametros {
    static final Map<String, String> SECCIONES = Map.ofEntries(
            Map.entry("costo_unitario_kwh", "tarifas"), Map.entry("consumo_subsistencia", "tarifas"),
            Map.entry("incremento_anual_tarifa", "tarifas"), Map.entry("consumo_maximo", "rangos"),
            Map.entry("umbral_advertencia_consumo", "rangos"), Map.entry("umbral_diferencia_factura", "rangos"),
            Map.entry("hsp", "tecnicos"), Map.entry("factor_rendimiento", "tecnicos"),
            Map.entry("factor_aprovechamiento", "tecnicos"), Map.entry("relacion_cc_ca", "tecnicos"),
            Map.entry("vida_util", "tecnicos"), Map.entry("area_minima", "rangos"), Map.entry("area_maxima", "rangos"),
            Map.entry("tope_sobredimensionamiento", "tecnicos"), Map.entry("amplificacion_serie", "sombreado"));

    final List<Registro> registros = new ArrayList<>();

    CatalogoParametros(Datos datos) {
        datos.parametros().forEach(p -> {
            var r = registro("parametro", p.getClave(), SECCIONES.get(p.getClave()));
            r.numero("valor", p.getValor(), 14, 4, p::setValor);
            r.valores.put("unidad", p.getUnidad());
            r.valores.put("descripcion", p.getDescripcion());
            r.valores.put("fecha_actualizacion", p.getFechaActualizacion());
        });
        datos.estratos().forEach(e -> {
            var r = registro("estrato", "" + e.getNumero(), "tarifas");
            r.numero("porcentaje_ajuste", e.getPorcentajeAjuste(), 5, 2, e::setPorcentajeAjuste);
            r.valores.put("tipo_ajuste", e.getTipoAjuste().name().toLowerCase(Locale.ROOT));
        });
        datos.franjas().forEach(f -> {
            var r = registro("franja_horaria", "" + f.getId(), "sombreado");
            r.valores.put("nombre", f.getNombre());
            r.valores.put("orden", f.getOrden());
            r.campo("hora_inicio", f.getHoraInicio(), Tipo.HORA, 0, 0, v -> f.setHoraInicio((LocalTime) v));
            r.campo("hora_fin", f.getHoraFin(), Tipo.HORA, 0, 0, v -> f.setHoraFin((LocalTime) v));
            r.numero("peso", f.getPeso(), 4, 3, f::setPeso);
        });
        datos.porciones().forEach(p -> {
            var r = registro("porcion_afectada", "" + p.getId(), "sombreado");
            r.valores.put("etiqueta", p.getEtiqueta());
            r.valores.put("orden", p.getOrden());
            r.numero("valor", p.getValor(), 4, 3, p::setValor);
        });
        datos.configuraciones().forEach(c -> {
            var r = registro("configuracion", "" + c.getId(), "costos", "sombreado");
            r.valores.put("codigo", c.getCodigo());
            r.valores.put("nombre", c.getNombre());
            r.numero("costo_kwp", c.getCostoKwp(), 12, 2, c::setCostoKwp);
            if (c.getCodigo().equals("mlpe")) {
                r.numero("coeficiente_recuperacion", c.getCoeficienteRecuperacion(), 4, 3, c::setCoeficienteRecuperacion);
            } else {
                r.valores.put("coeficiente_recuperacion", c.getCoeficienteRecuperacion());
            }
        });
        datos.tramos().forEach(t -> {
            var r = registro("tramo_sombreado", "" + t.getId(), "sombreado");
            r.valores.put("nombre", t.getNombre());
            r.valores.put("orden", t.getOrden());
            r.numero("factor_minimo", t.getFactorMinimo(), 4, 3, t::setFactorMinimo);
            r.numero("factor_maximo", t.getFactorMaximo(), 4, 3, t::setFactorMaximo);
            r.campo("muestra_advertencia", t.isMuestraAdvertencia(), Tipo.BOOLEANO, 0, 0,
                    v -> t.setMuestraAdvertencia((Boolean) v));
            r.campo("configuraciones", t.getConfiguraciones().stream().map(c -> c.getId()).sorted().toList(),
                    Tipo.CONFIGURACIONES, 0, 0, v -> {
                        // La lista ya fue contrastada íntegramente con las configuraciones existentes.
                        t.getConfiguraciones().clear();
                        for (Object id : (List<?>) v) {
                            t.getConfiguraciones().add(datos.configuraciones().stream()
                                    .filter(c -> c.getId().equals(id)).findFirst().orElseThrow());
                        }
                    });
        });
        datos.rangos().forEach(rango -> {
            var r = registro("rango_viabilidad", "" + rango.getId(), "rangos");
            r.valores.put("clasificacion", rango.getClasificacion());
            r.valores.put("orden", rango.getOrden());
            r.numero("anios_minimo", rango.getAniosMinimo(), 5, 2, rango::setAniosMinimo);
            r.numero("anios_maximo", rango.getAniosMaximo(), 5, 2, rango::setAniosMaximo);
        });
    }

    List<Registro> tabla(String tabla) {
        return registros.stream().filter(r -> r.tabla.equals(tabla)).toList();
    }

    Registro buscar(String tabla, String id) {
        return registros.stream().filter(r -> r.tabla.equals(tabla) && r.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La tabla o el registro solicitado no está permitido o no existe."));
    }

    private Registro registro(String tabla, String id, String... secciones) {
        var registro = new Registro(tabla, id, Arrays.asList(secciones));
        registros.add(registro);
        return registro;
    }

    enum Tipo { NUMERO, HORA, BOOLEANO, CONFIGURACIONES }
    record Campo(Tipo tipo, int precision, int escala, Consumer<Object> guardar) {}

    static final class Registro {
        final String tabla;
        final String id;
        final List<String> secciones;
        final Map<String, Object> valores = new LinkedHashMap<>();
        final Map<String, Campo> campos = new LinkedHashMap<>();

        Registro(String tabla, String id, List<String> secciones) {
            this.tabla = tabla;
            this.id = id;
            this.secciones = secciones;
        }

        void numero(String nombre, BigDecimal valor, int precision, int escala, Consumer<BigDecimal> guardar) {
            campo(nombre, valor, Tipo.NUMERO, precision, escala, v -> guardar.accept((BigDecimal) v));
        }

        void campo(String nombre, Object valor, Tipo tipo, int precision, int escala, Consumer<Object> guardar) {
            valores.put(nombre, valor);
            campos.put(nombre, new Campo(tipo, precision, escala, guardar));
        }

        ConsultaParametros.Registro dto() {
            return new ConsultaParametros.Registro(tabla, id, Collections.unmodifiableMap(new LinkedHashMap<>(valores)));
        }

        BigDecimal numero(String campo) { return (BigDecimal) valores.get(campo); }
    }
}
