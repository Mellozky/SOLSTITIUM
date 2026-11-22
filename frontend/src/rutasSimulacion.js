import { useEffect, useRef, useState } from 'react';

const rutas = ['/simulacion/consumo', '/simulacion/vivienda', '/simulacion/sombra', '/simulacion/resultados'];
const claveAnterior = 'pasoAnteriorSimulacion';

function leerPaso() {
  return Math.max(0, rutas.indexOf(window.location.pathname));
}

export function navegarPaso(historial, actual, siguiente) {
  if (siguiente > actual) {
    historial.pushState({ [claveAnterior]: actual }, '', rutas[siguiente]);
  } else if (historial.state?.[claveAnterior] === siguiente) {
    historial.back();
    return false;
  } else {
    historial.replaceState(historial.state, '', rutas[siguiente]);
  }
  return true;
}

export function usarRutaSimulacion(pasoDisponible) {
  const [pasoSolicitado, cambiarPasoSolicitado] = useState(leerPaso);
  const retrocesoPendiente = useRef(false);
  const paso = Math.min(pasoSolicitado, pasoDisponible);

  useEffect(() => {
    const actualizar = () => {
      retrocesoPendiente.current = false;
      cambiarPasoSolicitado(leerPaso());
    };
    window.addEventListener('popstate', actualizar);
    return () => window.removeEventListener('popstate', actualizar);
  }, []);

  useEffect(() => {
    if (pasoSolicitado !== paso) {
      window.history.replaceState(window.history.state, '', rutas[paso]);
      cambiarPasoSolicitado(paso);
    }
  }, [paso, pasoSolicitado]);

  function cambiarPaso(siguiente) {
    if (siguiente === paso || retrocesoPendiente.current) return;
    if (navegarPaso(window.history, paso, siguiente)) {
      cambiarPasoSolicitado(siguiente);
    } else {
      retrocesoPendiente.current = true;
    }
  }

  return [paso, cambiarPaso];
}
