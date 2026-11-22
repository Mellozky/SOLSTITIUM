import { useEffect, useState } from 'react';
import { Consumo } from './Consumo.jsx';
import { MAXIMO_MESES } from './entrada.js';

const pasos = ['Consumo', 'Vivienda y techo', 'Sombra', 'Resultados'];

export function Simulador() {
  const [meses, cambiarMeses] = useState(() => Array(MAXIMO_MESES).fill(''));
  const [factura, cambiarFactura] = useState('');
  const [parametros, cambiarParametros] = useState(null);
  const [estadoCarga, cambiarEstadoCarga] = useState('cargando');
  const [intento, cambiarIntento] = useState(0);
  const [entrada, cambiarEntrada] = useState(null);

  useEffect(() => {
    const control = new AbortController();
    fetch('/api/simulaciones/parametros-entrada', { signal: control.signal, cache: 'no-store' })
      .then((respuesta) => {
        if (!respuesta.ok) throw new Error();
        return respuesta.json();
      })
      .then((datos) => {
        cambiarParametros(datos);
        cambiarEstadoCarga('lista');
      })
      .catch(() => {
        if (!control.signal.aborted) cambiarEstadoCarga('error');
      });
    return () => control.abort();
  }, [intento]);

  return (
    <>
      <header className="cabecera">
        <div className="contenedor cabecera-contenido">
          <strong><span className="sol" aria-hidden="true">☀</span> Simulador Solar Montería</strong>
          <small>Estimación gratuita · sin registro · tus datos no se guardan</small>
        </div>
      </header>
      <main className="contenedor">
        <nav aria-label="Progreso de la simulación">
          <ol className="pasos">
            {pasos.map((nombre, indice) => (
              <li key={nombre} aria-current={indice === 0 ? 'step' : undefined}>
                <span aria-hidden="true">{indice + 1}</span>{nombre}
              </li>
            ))}
          </ol>
        </nav>
        {estadoCarga === 'error' && (
          <div className="aviso" role="alert">
            No pudimos cargar los parámetros de la simulación.
            {' '}
            <button type="button" onClick={() => {
              cambiarEstadoCarga('cargando');
              cambiarIntento(intento + 1);
            }}>Reintentar</button>
          </div>
        )}
        <Consumo
          meses={meses}
          factura={factura}
          cambiarMeses={cambiarMeses}
          cambiarFactura={cambiarFactura}
          parametros={parametros}
          alContinuar={cambiarEntrada}
        />
        {entrada && <p role="status">Consumos preparados para la simulación.</p>}
      </main>
    </>
  );
}
