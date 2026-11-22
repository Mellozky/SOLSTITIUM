# Cálculo del factor de sombra (RF-03)

`CalculadoraSombra.calcularFactor(respuestas, conjunto)` recibe las respuestas por franja y los parámetros ya cargados desde PostgreSQL. No consulta nuevamente la base ni almacena la simulación.

Para cada franja:

`pérdida de la franja = peso × min(valor de la porción × amplificación en serie, 1)`

Después suma las pérdidas y obtiene `factor = 1 − pérdida`. Se usa `BigDecimal` sin redondear antes de clasificar. El límite de uno se aplica dentro de cada franja: aplicarlo solo a la suma alteraría el modelo del ERS.

El resultado interno `FactorSombra` contiene `perdida`, `factor` y `tramo`, conforme a la figura 5. El tramo aporta sus configuraciones y `muestraAdvertencia`; el servicio de simulación pendiente traducirá esos datos al resultado público y al aviso de baja confianza.

Con los datos supuestos de V2:

| Tramo | Factor | Configuraciones | Advertencia |
|---|---|---|---|
| Sin sombra | 1 | Central | No |
| Leve | Desde 0,90 hasta menos de 1 | Central | No |
| Moderada | Desde 0,75 hasta menos de 0,90 | Central | No |
| Alta | Desde 0,55 hasta menos de 0,75 | Central y MLPE | No |
| Severa | Desde 0 hasta menos de 0,55 | Central y MLPE | Sí |

Los extremos y asociaciones salen de las tablas: no se clasifican por nombres ni por umbrales fijos en Java. Los tramos usan mínimo incluido y máximo excluido; un tramo puntual como `[1,1]` acepta únicamente ese punto. Debe existir una sola coincidencia para el factor calculado.

Se rechazan respuestas nulas, ausentes, duplicadas o con identificadores desconocidos. La calculadora también detecta pesos que no suman uno, valores fuera de dominio, amplificación inválida, ausencia o ambigüedad del tramo y un tramo elegido sin configuraciones. Estos controles no sustituyen al futuro validador administrativo de toda la configuración.

La sombra total produce factor cero sin divisiones; el dimensionamiento usa su margen máximo en ese caso. La recuperación MLPE se aplica después en `CalculadoraFinanciera`, sin alterar el factor original ni los paneles dimensionados. El servicio coordina estos pasos y la API se describe en `contrato-simulacion.md`.

## Formulario de Sombra (CU-04)

- `GET /api/simulaciones/parametros-entrada` devuelve `franjas` con `id`, `nombre`, `horaInicio` y `horaFin` en formato ISO, y `porciones` con `id` y `etiqueta`. Ambas listas siguen el orden del catálogo en PostgreSQL.
- `POST /api/simulaciones/vista-previa-sombra` recibe un objeto con solo `respuestasSombra`, en el mismo formato que la solicitud final. Devuelve `{"tramo":"Alta"}` para CP-04. Es una operación pública de solo lectura. Una franja ausente, duplicada o desconocida, o una porción inválida, devuelve HTTP 400 con el contrato de errores de simulación.
- El formulario muestra el tramo antes de enviar la solicitud completa a `POST /api/simulaciones`. Si falla el envío, conserva las selecciones y muestra el error.

Para reproducir CP-04 con V2, ingresa de arriba a abajo 280, 320 y 300 kWh, deja la factura vacía y selecciona estrato 4 y 40 m². En Sombra, elige **Un borde o una esquina** para Mañana y Mediodía y **Cerca de una cuarta parte** para Tarde. La vista previa indica **Alta**. Resultados muestra siete paneles por configuración y recomienda el inversor central. La API recibe `[300,320,280]` y devuelve un factor de 0,6325.

Para verificar CV-15 durante ese recorrido, deja Tarde sin responder y pulsa **Ver resultados**. El formulario señala esa franja y conserva el paso y las selecciones.
