# Coordinación de simulación (RF-01 a RF-06)

`SimulacionService.simular(solicitud)` conserva el nombre del ERS, carga los parámetros una vez y coordina validación, sombra, dimensionamiento y finanzas en una transacción de lectura repetible. No guarda datos del residente. El controlador HTTP se incorpora en el siguiente incremento.

Devuelve `ResultadoSimulacion`, con tramo, resultados, recomendación y motivo, escenario sin sombra, advertencias, panel de referencia y fecha de actualización de la tarifa. Se añaden `factorSombra` y `gastoMensualSinSistema`; cada configuración incluye `gastoMensualConSistema`, para que el gráfico no replique reglas tarifarias en el navegador. No se exponen entidades JPA.

La recomendación compara tiempos de recuperación sin redondear y excluye los que no recuperan en la vida útil. En un empate exacto conserva el orden de configuraciones del catálogo. Si ninguna recupera, devuelve `recomendada: null` y explica el motivo. Una configuración que recupera en 14 años puede recomendarse aunque su rango sea “No viable”, como en CP-08: no se confunde la clasificación con la ausencia de recuperación.

El escenario sin sombra se calcula únicamente cuando el factor original es menor que uno: factor uno, pérdida cero, inversor central y nuevo dimensionamiento sin margen de sombra. No modifica la solicitud original ni depende de que el catálogo conserve una porción etiquetada como “Nada”.

Con cero paneles devuelve `sinEspacio: true`, resultados vacíos y ningún indicador financiero. Las advertencias y metadatos se conservan. En todos los casos se indica que es una estimación, no una cotización; el tramo puede añadir una advertencia de baja confianza y sugerir evaluación en sitio.
