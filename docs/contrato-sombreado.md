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

La sombra total produce factor cero sin divisiones; el posterior dimensionamiento deberá usar su margen máximo en ese caso. Todavía no se calcula la recuperación MLPE ni se ejecuta una simulación completa: son pasos posteriores.
