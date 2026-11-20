package com.solstitium.solstitium.service;

import com.solstitium.solstitium.model.dto.CambioParametro;
import com.solstitium.solstitium.repository.AdministracionParametrosRepository.Datos;
import com.solstitium.solstitium.service.calculo.LimitesCalculoFinanciero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Valida primero valores individuales; después, el estado completo propuesto, sin persistirlo. */
@Component
public class ValidadorParametros {
    Object validarValor(CambioParametro cambio, CatalogoParametros.Registro registro, Datos datos) {
        var campo = registro.campos.get(cambio.campo());
        exigir(campo != null && (!registro.tabla.equals("parametro")
                || CatalogoParametros.SECCIONES.containsKey(registro.id)), "El campo solicitado es de solo lectura o no está permitido.");
        Object valor = cambio.valorNuevo();
        if (campo.tipo() == CatalogoParametros.Tipo.BOOLEANO) {
            exigir(valor instanceof Boolean, "La advertencia debe ser un booleano.");
            return valor;
        }
        if (campo.tipo() == CatalogoParametros.Tipo.CONFIGURACIONES) return configuraciones(valor, datos);

        BigDecimal numero;
        Object convertido = valor;
        if (campo.tipo() == CatalogoParametros.Tipo.HORA) {
            exigir(valor instanceof String && ((String) valor).matches("[0-2][0-9]:[0-5][0-9]"), "La hora debe tener formato HH:mm.");
            try {
                LocalTime hora = LocalTime.parse((String) valor);
                convertido = hora;
                numero = BigDecimal.valueOf(hora.getHour() * 60L + hora.getMinute());
            } catch (DateTimeParseException excepcion) {
                throw new IllegalArgumentException("La hora no es válida.");
            }
        } else {
            boolean nuloPermitido = registro.tabla.equals("rango_viabilidad") && cambio.campo().equals("anios_maximo");
            exigir(valor instanceof BigDecimal || (valor == null && nuloPermitido), "Debe enviar un número JSON válido.");
            numero = (BigDecimal) valor;
            if (numero != null) {
                try {
                    BigDecimal escalado = numero.setScale(campo.escala(), RoundingMode.UNNECESSARY);
                    exigir(escalado.precision() <= campo.precision(), "El valor excede la precisión admitida.");
                } catch (ArithmeticException excepcion) {
                    throw new IllegalArgumentException("El valor tiene más decimales de los admitidos.");
                }
                if (registro.tabla.equals("parametro") && registro.id.equals("vida_util")) {
                    exigir(numero.stripTrailingZeros().scale() <= 0, "La vida útil debe ser un número entero.");
                }
            }
        }
        String clave = registro.tabla.equals("parametro") ? registro.id : null;
        var limite = datos.limites().stream().filter(l -> l.getTabla().equals(registro.tabla)
                && l.getCampo().equals(cambio.campo()) && Objects.equals(l.getClave(), clave)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Falta el límite de edición del campo solicitado."));
        if (numero != null) {
            exigir(numero.compareTo(limite.getValorMinimo()) >= 0 && numero.compareTo(limite.getValorMaximo()) <= 0,
                    "El valor de " + registro.tabla + "." + cambio.campo() + " debe estar entre "
                            + limite.getValorMinimo().toPlainString() + " y " + limite.getValorMaximo().toPlainString() + ".");
        }
        return convertido;
    }

    private List<Long> configuraciones(Object valor, Datos datos) {
        exigir(valor instanceof List<?> && !((List<?>) valor).isEmpty(), "Debe seleccionar configuraciones existentes.");
        List<Long> ids = new ArrayList<>();
        for (Object elemento : (List<?>) valor) {
            exigir(elemento instanceof BigDecimal, "Las configuraciones deben ser identificadores enteros.");
            long id;
            try { id = ((BigDecimal) elemento).longValueExact(); }
            catch (ArithmeticException excepcion) { throw new IllegalArgumentException("El identificador de configuración debe ser entero."); }
            exigir(datos.configuraciones().stream().anyMatch(c -> c.getId() == id), "La configuración no existe.");
            exigir(!ids.contains(id), "No se puede repetir una configuración.");
            ids.add(id);
        }
        return ids.stream().sorted().toList();
    }

    void validarCoherencia(CatalogoParametros catalogo, Datos datos) {
        exigir(!datos.parametros().isEmpty() && !datos.estratos().isEmpty() && !datos.franjas().isEmpty()
                && !datos.porciones().isEmpty() && !datos.tramos().isEmpty() && !datos.rangos().isEmpty(),
                "El conjunto de parámetros está incompleto.");
        exigir(datos.paneles().stream().filter(p -> p.isEsReferencia()).count() == 1
                && datos.paneles().stream().anyMatch(p -> p.isEsReferencia() && p.isActivo()),
                "Debe existir exactamente un panel de referencia activo.");
        for (String clave : CatalogoParametros.SECCIONES.keySet()) catalogo.buscar("parametro", clave);
        LimitesCalculoFinanciero.validarHorizonte(parametro(catalogo, "vida_util"), parametro(catalogo, "incremento_anual_tarifa"));
        for (var configuracion : catalogo.tabla("configuracion")) {
            if (configuracion.valores.get("codigo").equals("central")) {
                exigir(configuracion.numero("coeficiente_recuperacion").signum() == 0,
                        "La configuración central debe conservar coeficiente de recuperación cero; solo MLPE recupera pérdidas por sombra.");
            }
        }
        var franjas = catalogo.tabla("franja_horaria");
        BigDecimal pesos = franjas.stream().map(r -> r.numero("peso")).reduce(BigDecimal.ZERO, BigDecimal::add);
        exigir(pesos.compareTo(BigDecimal.ONE) == 0, "Los pesos de las franjas deben sumar exactamente 1.");
        LocalTime finAnterior = null;
        for (var franja : franjas) {
            var inicio = (LocalTime) franja.valores.get("hora_inicio");
            var fin = (LocalTime) franja.valores.get("hora_fin");
            exigir(inicio.isBefore(fin) && (finAnterior == null || !inicio.isBefore(finAnterior)),
                    "Las franjas horarias deben estar ordenadas y no cruzarse.");
            finAnterior = fin;
        }
        BigDecimal porcionAnterior = null;
        for (var porcion : catalogo.tabla("porcion_afectada")) {
            BigDecimal actual = porcion.numero("valor");
            exigir(porcionAnterior == null || actual.compareTo(porcionAnterior) > 0, "Las porciones deben ser estrictamente crecientes.");
            porcionAnterior = actual;
        }
        validarTramos(catalogo, datos);
        validarRangos(catalogo);
        exigir(parametro(catalogo, "area_minima").compareTo(parametro(catalogo, "area_maxima")) <= 0,
                "El área mínima no puede superar el área máxima.");
        exigir(parametro(catalogo, "umbral_advertencia_consumo").compareTo(parametro(catalogo, "consumo_maximo")) <= 0,
                "El umbral de consumo no puede superar el consumo máximo.");
        for (var estrato : catalogo.tabla("estrato")) {
            if (estrato.valores.get("tipo_ajuste").equals("ninguno"))
                exigir(estrato.numero("porcentaje_ajuste").signum() == 0, "Un estrato sin ajuste debe conservar porcentaje cero.");
        }
    }

    private void validarTramos(CatalogoParametros catalogo, Datos datos) {
        var central = datos.configuraciones().stream().filter(c -> c.getCodigo().equals("central")).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Debe existir la configuración central."));
        BigDecimal siguiente = BigDecimal.ZERO;
        int puntos = 0;
        var tramos = catalogo.tabla("tramo_sombreado").stream()
                .sorted(Comparator.comparing(r -> r.numero("factor_minimo"))).toList();
        for (var tramo : tramos) {
            exigir(((List<?>) tramo.valores.get("configuraciones")).contains(central.getId()),
                    "Cada tramo debe incluir la configuración central.");
            var minimo = tramo.numero("factor_minimo");
            var maximo = tramo.numero("factor_maximo");
            if (minimo.compareTo(BigDecimal.ONE) == 0 && maximo.compareTo(BigDecimal.ONE) == 0) {
                puntos++;
            } else {
                exigir(minimo.compareTo(siguiente) == 0 && maximo.compareTo(minimo) > 0
                        && maximo.compareTo(BigDecimal.ONE) <= 0, "Los tramos deben cubrir [0,1) sin huecos ni cruces.");
                siguiente = maximo;
            }
        }
        exigir(siguiente.compareTo(BigDecimal.ONE) == 0 && puntos == 1, "Debe cubrirse [0,1) y existir un único tramo [1,1].");
    }

    private void validarRangos(CatalogoParametros catalogo) {
        var rangos = catalogo.tabla("rango_viabilidad");
        BigDecimal siguiente = BigDecimal.ZERO;
        for (int indice = 0; indice < rangos.size(); indice++) {
            var rango = rangos.get(indice);
            var minimo = rango.numero("anios_minimo");
            var maximo = rango.numero("anios_maximo");
            exigir(minimo.compareTo(siguiente) == 0, "Los rangos deben cubrir desde cero sin huecos ni cruces.");
            if (indice == rangos.size() - 1) {
                exigir(maximo == null, "El último rango debe quedar abierto, sin máximo.");
            } else {
                exigir(maximo != null && maximo.compareTo(minimo) > 0, "Cada rango anterior al último debe tener un máximo mayor al mínimo.");
                siguiente = maximo;
            }
        }
    }

    private BigDecimal parametro(CatalogoParametros catalogo, String clave) {
        return catalogo.buscar("parametro", clave).numero("valor");
    }

    private void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new IllegalArgumentException(mensaje);
    }
}
