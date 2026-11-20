# Entrada de datos de simulación

Contrato preparado para el futuro servicio y controlador; todavía no existe un endpoint de simulación.
Los DTO siguen la figura 5 del ERS y no se guardan en tablas.

```json
{
  "consumos": [160, 150, 140],
  "valorFactura": 75000,
  "estrato": 1,
  "areaLibre": 40,
  "respuestasSombra": [
    {"franjaId": 1, "porcionId": 1},
    {"franjaId": 2, "porcionId": 1},
    {"franjaId": 3, "porcionId": 1}
  ]
}
```

- `consumos`: de 1 a 12 meses, en orden cronológico; el último corresponde a la factura opcional. Todos deben ser positivos, no nulos y no superar el máximo configurable. Se advierte por cada mes que supera el umbral, sin rechazarlo.
- `valorFactura`: opcional; se puede omitir o enviar `null`. Si se informa debe ser positivo. La comparación con la tarifa estimada queda pendiente del cálculo de tarifa (CV-13 y CV-14).
- `estrato`: obligatorio, entero de 1 a 6 y presente en los parámetros. Se usa `Integer` en Java para detectar su ausencia; el ERS lo representa como `int`.
- `areaLibre`: área libre del techo en m², obligatoria y dentro de los extremos inclusivos configurados.
- `respuestasSombra`: exactamente una respuesta por franja del catálogo. No se admiten franjas duplicadas, ausentes o inexistentes ni porciones desconocidas. El residente elige opciones: no envía coeficientes técnicos. Los identificadores del ejemplo corresponden a V2, no son constantes del validador.

`ValidadorSolicitudSimulacion.validar(solicitud, conjunto)` verifica primero las anotaciones Jakarta Validation y después los límites e identificadores del `ConjuntoParametros` cargado por `ParametroRepository`. Devuelve advertencias no bloqueantes o lanza `DatosSimulacionInvalidosException` con una lista de errores en español. La futura API deberá traducir esta excepción a HTTP 400 mediante su manejador de errores.

Con V2: 800 kWh no advierte, 801 y 2000 sí; 2001 se rechaza. Área 5 y 500 m² se acepta; 4,99 y 500,01 se rechazan. Estos límites salen de `parametro`, no de las anotaciones.

`SolicitudSimulacion.consumoPromedio()` usa `BigDecimal` y `MathContext.DECIMAL128` para la división. Se llama después de validar; no redondea los consumos a enteros ni calcula la tarifa.

Se incorpora un validador de entrada separado del futuro `ValidadorParametros` del ERS, que validará cambios administrativos. No se añade todavía controlador, cálculo de sombra, dimensionamiento ni indicadores financieros.
