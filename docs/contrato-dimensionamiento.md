# Dimensionamiento técnico (RF-04)

`CalculadoraTecnica.dimensionar(solicitud, sombra, configuracion, conjunto)` devuelve un `ResultadoConfiguracion`, con la firma de la figura 4 del ERS. El futuro servicio llamará al método para cada configuración del tramo. No se consulta la base nuevamente ni se almacena una simulación.

El cálculo sigue este orden:

1. Potencia teórica = consumo promedio / (30 × HSP × rendimiento).
2. Margen = mínimo de (1 / factor de sombra − 1) y el tope configurable. Con factor cero, se usa directamente el tope.
3. Paneles necesarios: redondeo hacia arriba de la potencia con margen / potencia del panel de referencia en kWp.
4. Paneles que caben: redondeo hacia abajo de área libre × aprovechamiento / área del panel.
5. Paneles finales: el menor de los anteriores. Potencia instalada = paneles finales × Wp del panel / 1000.
6. Inversor: potencia instalada / relación CC/CA, redondeada hacia arriba a múltiplos de 0,5 kW.
7. Inversión: potencia instalada × costo por kWp de la configuración elegida.

HSP, rendimiento, aprovechamiento, relación CC/CA, tope, panel y costo por configuración vienen del conjunto cargado desde PostgreSQL. El valor `25` del tope representa 25 %, no una fracción. Central y MLPE usan los mismos paneles e inversor; difiere su costo por kWp.

Para evitar un panel adicional por errores de redondeo, la implementación combina suma y cantidad de consumos en una razón y aplica `CEILING` solo al cociente final. La compensación por sombra se representa multiplicando el denominador por el factor cuando no se alcanza el tope. No se redondea anticipadamente el promedio ni el recíproco del factor. Todas las operaciones usan `BigDecimal`.

Si no cabe ningún panel, el resultado técnico tiene cero paneles, potencia, inversor e inversión. Los campos financieros quedan nulos: no se calcula ahorro, recuperación, ROI ni clasificación. El servicio pendiente deberá convertir este caso en `sinEspacio` y omitir los indicadores y resultados financieros, como indica el ERS. CV-29 se prueba directamente con 3 m²; el formulario sigue rechazando áreas menores al mínimo configurable (5 m² con V2).

`ResultadoConfiguracion` conserva los campos de la figura 5. Este incremento completa solo configuración, paneles, potencia, inversor e inversión. Los demás campos serán completados por `CalculadoraFinanciera`; nulo no significa ahorro cero ni “no viable”. Todavía no se expone un endpoint.
