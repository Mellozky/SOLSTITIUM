# Cálculo financiero

## Generación y ahorro (RF-05)

`CalculadoraFinanciera.calcularGeneracionYAhorro(resultado, solicitud, conjunto)` completa energía mensual aprovechada, ahorro y cobertura. No modifica el dimensionamiento ni consulta nuevamente la base.

El factor efectivo es `1 − pérdida × (1 − recuperación de la configuración)`. Central tiene recuperación cero; MLPE usa el coeficiente del catálogo. La generación es potencia × HSP × rendimiento × factor efectivo × 30, limitada al consumo promedio para obtener la energía aprovechada (campo `energiaMensual`). No se remunera energía excedente.

El ahorro es costo del consumo original menos costo del consumo restante, ambos calculados por estrato. Así se reduce primero el tramo pleno y después el subsidiado. La cobertura es energía aprovechada / consumo × 100. Se usa `BigDecimal`, sin redondeos monetarios intermedios.

Con cero paneles se omiten los indicadores. Los valores del ERS usados en pruebas son supuestos, no precios reales.

## Recuperación, ROI y viabilidad (RF-06)

`calcularIndicadores(resultado, solicitud, conjunto)` completa RF-05 y la rentabilidad. `calcularRentabilidad` permite comprobar por separado la inversión y el ahorro ya calculados.

El primer año ahorra doce veces el ahorro mensual; cada año posterior aplica el incremento porcentual configurado. El acumulado usa la suma geométrica (o multiplicación lineal si el incremento es cero). Se busca el primer año que cubre la inversión y se interpola dentro de él. No se redondea el tiempo antes de clasificar o recomendar.

Si no recupera dentro de la vida útil, `tiempoRecuperacion` es `null` y se usa la clasificación del rango abierto final. Ahorro cero produce ROI −100 %. El ROI es `(ahorro total − inversión) / inversión × 100`. Los rangos usan mínimo excluido y máximo incluido, salvo el inicio cero. Debe existir una clasificación única.

La vida útil debe ser entera y positiva. La búsqueda binaria evita recorrer año por año; se respeta el límite técnico de exponente de `BigDecimal.pow`. Los rangos y la vida útil proceden de PostgreSQL. La ausencia de recuperación se diferencia del resultado sin espacio: este último conserva los indicadores nulos y no se clasifica.
