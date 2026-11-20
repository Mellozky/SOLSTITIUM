package com.solstitium.solstitium.apoyo;

import com.solstitium.solstitium.model.dto.RespuestaSombra;
import com.solstitium.solstitium.model.dto.SolicitudSimulacion;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.IntStream;

/** Datos de entrada compartidos por la regresión del ERS; no forma parte del producto. */
public final class SolicitudesPrueba {
    private SolicitudesPrueba() {}

    public static SolicitudSimulacion crear(int estrato, String consumos, String area, int manana, int mediodia, int tarde) {
        SolicitudSimulacion solicitud = new SolicitudSimulacion();
        solicitud.setEstrato(estrato);
        solicitud.setConsumos(Arrays.stream(consumos.split("\\|")).map(BigDecimal::new).toList());
        solicitud.setAreaLibre(new BigDecimal(area));
        int[] porciones = {manana, mediodia, tarde};
        solicitud.setRespuestasSombra(IntStream.range(0, 3).mapToObj(indice -> {
            RespuestaSombra respuesta = new RespuestaSombra();
            respuesta.setFranjaId((long) indice + 1);
            respuesta.setPorcionId((long) porciones[indice]);
            return respuesta;
        }).toList());
        return solicitud;
    }
}
