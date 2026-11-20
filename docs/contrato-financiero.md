# Cálculo financiero

## Generación y ahorro (RF-05)

`CalculadoraFinanciera.calcularGeneracionYAhorro(resultado, solicitud, conjunto)` completa energía mensual aprovechada, ahorro y cobertura. No modifica el dimensionamiento ni consulta nuevamente la base.

El factor efectivo es `1 − pérdida × (1 − recuperación de la configuración)`. Central tiene recuperación cero; MLPE usa el coeficiente del catálogo. La generación es potencia × HSP × rendimiento × factor efectivo × 30, limitada al consumo promedio para obtener la energía aprovechada (campo `energiaMensual`). No se remunera energía excedente.

El ahorro es costo del consumo original menos costo del consumo restante, ambos calculados por estrato. Así se reduce primero el tramo pleno y después el subsidiado. La cobertura es energía aprovechada / consumo × 100. Se usa `BigDecimal`, sin redondeos monetarios intermedios.

Con cero paneles se omiten los indicadores. La rentabilidad, la clasificación y la comparación de configuraciones se incorporan en los incrementos siguientes. Los valores del ERS usados en pruebas son supuestos, no precios reales.
