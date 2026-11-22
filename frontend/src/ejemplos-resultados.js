// Respuestas reales de POST /api/simulaciones, copiadas sin modificar, con los datos de prueba
// de V2 (capítulo 10 del ERS). Son supuestos de prueba, no tarifas ni cotizaciones reales.

/**
 * CP-04: estrato 4, 280·320·300 kWh, 40 m², sombra alta. Se recomienda el inversor central.
 */
export const conRecomendacion = {
  "advertencias": [
    "Esta simulación es una estimación, no una cotización."
  ],
  "escenarioSinSombra": {
    "ahorroMensual": 300000.0,
    "clasificacion": "Viable",
    "cobertura": 100,
    "configuracion": "central",
    "costoTotal": 13200000.0,
    "energiaMensual": 300,
    "gastoMensualConSistema": 0.0,
    "inversorKw": 3.0,
    "paneles": 6,
    "potenciaKwp": 3.3,
    "roi": 1201.6481495814826,
    "tiempoRecuperacion": 3.444156498578267
  },
  "factorSombra": 0.6325,
  "fechaTarifa": "2026-11-21T23:54:24.620574",
  "gastoMensualSinSistema": 300000.0,
  "motivoRecomendacion": "Se recomienda central por su menor tiempo de recuperación.",
  "panelReferencia": "DEMOSTRACIÓN Referencia ERS 550",
  "recomendada": "central",
  "resultados": [
    {
      "ahorroMensual": 262993.5,
      "clasificacion": "Viable",
      "cobertura": 87.6645,
      "configuracion": "central",
      "costoTotal": 15400000.0,
      "energiaMensual": 262.9935,
      "gastoMensualConSistema": 37006.5,
      "inversorKw": 3.5,
      "paneles": 7,
      "potenciaKwp": 3.85,
      "roi": 878.0714360770219,
      "tiempoRecuperacion": 4.468603122890204
    },
    {
      "ahorroMensual": 300000.0,
      "clasificacion": "Viable",
      "cobertura": 100,
      "configuracion": "mlpe",
      "costoTotal": 17710000.0,
      "energiaMensual": 300,
      "gastoMensualConSistema": 0.0,
      "inversorKw": 3.5,
      "paneles": 7,
      "potenciaKwp": 3.85,
      "roi": 870.1725338495522,
      "tiempoRecuperacion": 4.501288614883259
    }
  ],
  "sinEspacio": false,
  "tramo": "Alta"
};

/**
 * 5 kWh/mes, estrato 1, 40 m², sombra total: ninguna configuración recupera la inversión.
 */
export const sinRecuperacion = {
  "advertencias": [
    "Esta simulación es una estimación, no una cotización.",
    "Baja confianza por sombreado: se recomienda una evaluación en sitio."
  ],
  "escenarioSinSombra": {
    "ahorroMensual": 2500.0,
    "clasificacion": "No viable",
    "cobertura": 100,
    "configuracion": "central",
    "costoTotal": 2200000.0,
    "energiaMensual": 5,
    "gastoMensualConSistema": 0.0,
    "inversorKw": 0.5,
    "paneles": 1,
    "potenciaKwp": 0.55,
    "roi": -34.91759252092587,
    "tiempoRecuperacion": null
  },
  "factorSombra": 0.0,
  "fechaTarifa": "2026-11-21T23:54:24.620574",
  "gastoMensualSinSistema": 2500.0,
  "motivoRecomendacion": "Ninguna configuración recupera la inversión durante la vida útil.",
  "panelReferencia": "DEMOSTRACIÓN Referencia ERS 550",
  "recomendada": null,
  "resultados": [
    {
      "ahorroMensual": 0.0,
      "clasificacion": "No viable",
      "cobertura": 0.0,
      "configuracion": "central",
      "costoTotal": 2200000.0,
      "energiaMensual": 0.0,
      "gastoMensualConSistema": 2500.0,
      "inversorKw": 0.5,
      "paneles": 1,
      "potenciaKwp": 0.55,
      "roi": -100,
      "tiempoRecuperacion": null
    },
    {
      "ahorroMensual": 2500.0,
      "clasificacion": "No viable",
      "cobertura": 100,
      "configuracion": "mlpe",
      "costoTotal": 2530000.0,
      "energiaMensual": 5,
      "gastoMensualConSistema": 0.0,
      "inversorKw": 0.5,
      "paneles": 1,
      "potenciaKwp": 0.55,
      "roi": -43.40660219210945,
      "tiempoRecuperacion": null
    }
  ],
  "sinEspacio": false,
  "tramo": "Severa"
};

/**
 * Cero paneles: sinEspacio, sin resultados ni indicadores financieros. Con V2 ninguna área válida
 * produce este caso (5 m² ya admite un panel); se obtuvo en una base de prueba con el panel de
 * referencia de 4 m² y un techo de 5 m².
 */
export const sinEspacio = {
  "advertencias": [
    "Esta simulación es una estimación, no una cotización."
  ],
  "escenarioSinSombra": null,
  "factorSombra": 0.79,
  "fechaTarifa": "2026-11-21T23:54:24.620574",
  "gastoMensualSinSistema": 224050.0,
  "motivoRecomendacion": "El techo no dispone de espacio suficiente para instalar un panel.",
  "panelReferencia": "DEMOSTRACIÓN Referencia ERS 550",
  "recomendada": null,
  "resultados": [],
  "sinEspacio": true,
  "tramo": "Moderada"
};
