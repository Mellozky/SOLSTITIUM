import { useEffect, useState } from 'react';

const rutas = ['/simulacion/consumo', '/simulacion/vivienda', '/simulacion/sombra'];

function leerPaso() {
  return Math.max(0, rutas.indexOf(window.location.pathname));
}

export function usarRutaSimulacion(pasoDisponible) {
  const [pasoSolicitado, cambiarPasoSolicitado] = useState(leerPaso);
  const paso = Math.min(pasoSolicitado, pasoDisponible);

  useEffect(() => {
    const actualizar = () => cambiarPasoSolicitado(leerPaso());
    window.addEventListener('popstate', actualizar);
    return () => window.removeEventListener('popstate', actualizar);
  }, []);

  useEffect(() => {
    if (pasoSolicitado !== paso) {
      window.history.replaceState(null, '', rutas[paso]);
      cambiarPasoSolicitado(paso);
    }
  }, [paso, pasoSolicitado]);

  function cambiarPaso(siguiente) {
    window.history.pushState(null, '', rutas[siguiente]);
    cambiarPasoSolicitado(siguiente);
  }

  return [paso, cambiarPaso];
}
