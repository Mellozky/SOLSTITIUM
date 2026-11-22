# Entrada de datos de simulación

Contrato de entrada de `POST /api/simulaciones`; la respuesta se documenta en `contrato-simulacion.md`.
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
- `valorFactura`: opcional; se puede omitir o enviar `null`. Si se informa debe ser positivo. Se compara con el costo del último mes ingresado (no con el promedio). Una diferencia absoluta superior al porcentaje configurable genera advertencia; estar exactamente en el umbral no advierte (CV-13 y CV-14). La advertencia no rechaza la solicitud ni modifica la tarifa.
- `estrato`: obligatorio, entero de 1 a 6 y presente en los parámetros. Se usa `Integer` en Java para detectar su ausencia; el ERS lo representa como `int`.
- `areaLibre`: área libre del techo en m², obligatoria y dentro de los extremos inclusivos configurados.
- `respuestasSombra`: exactamente una respuesta por franja del catálogo. No se admiten franjas duplicadas, ausentes o inexistentes ni porciones desconocidas. El residente elige opciones: no envía coeficientes técnicos. Los identificadores del ejemplo corresponden a V2, no son constantes del validador.

`ValidadorSolicitudSimulacion.validar(solicitud, conjunto)` verifica primero las anotaciones Jakarta Validation y después los límites e identificadores del `ConjuntoParametros` cargado por `ParametroRepository`. Devuelve advertencias no bloqueantes o lanza `DatosSimulacionInvalidosException` con una lista de errores en español. La API traduce esta excepción a HTTP 400 mediante su manejador de errores.

Con V2: 800 kWh no advierte, 801 y 2000 sí; 2001 se rechaza. Área 5 y 500 m² se acepta; 4,99 y 500,01 se rechazan. Estos límites salen de `parametro`, no de las anotaciones.

La interfaz consulta `GET /api/simulaciones/parametros-entrada` para obtener `consumoMaximo` y `umbralAdvertenciaConsumo` desde la base. Para mostrar el promedio sin completar la simulación, envía únicamente `{"consumos":[120,150]}` a `POST /api/simulaciones/promedio-consumo`, que devuelve el promedio como un número JSON (135 en el ejemplo). Ambas rutas son públicas. El promedio valida los consumos con el máximo vigente.

`SolicitudSimulacion.consumoPromedio()` usa `BigDecimal` y `MathContext.DECIMAL128` para la división. Se llama después de validar; no redondea los consumos a enteros ni calcula la tarifa.

El validador de entrada está separado del futuro `ValidadorParametros` del ERS, que validará cambios administrativos. El servicio coordina sombra, dimensionamiento e indicadores financieros; cada componente tiene su contrato en esta carpeta.

## Tarifa por estrato

`CalculadoraTarifa`, en `service/calculo`, concentra la regla de RF-02 para reutilizarla en la futura calculadora financiera. Es un componente auxiliar añadido para evitar duplicar la liquidación tarifaria; no reemplaza las tres calculadoras principales del ERS.

- Subsidio: `min(consumo, subsistencia) × costo_unitario × (1 − porcentaje/100) + max(consumo − subsistencia, 0) × costo_unitario`.
- Sin ajuste: `consumo × costo_unitario`.
- Contribución: `consumo × costo_unitario × (1 + porcentaje/100)`.

Los parámetros y porcentajes salen del `ConjuntoParametros` cargado desde PostgreSQL. No se obtiene una tarifa promedio para valorar todos los kWh: se conservan los tramos de subsistencia. La operación usa `BigDecimal` sin redondeos intermedios; solo el importe mostrado en la advertencia se presenta con dos decimales.

Con V2, 250 kWh del estrato 3 cuestan 224.050 COP. Facturas de 268.860 y 179.240 COP están exactamente a ±20 % y no advierten; 268.861 y 179.239 sí. El aviso sugiere revisar consumo y estrato y recuerda que puede haber otros cobros. La factura declarada nunca recalibra el costo unitario.
