import { useEffect, useRef, useState } from 'react';
import { Consumo } from './Consumo.jsx';
import { Vivienda } from './Vivienda.jsx';
import { MAXIMO_MESES, prepararEntradaSimulacion } from './entrada.js';
import { usarRutaSimulacion } from './rutasSimulacion.js';
import { solicitarJson } from './clienteApi.js';
import { MensajeApi } from './MensajeApi.jsx';

const pasos = ['Consumo', 'Vivienda y techo', 'Sombra', 'Resultados'];

export function Simulador() {
  const [meses, cambiarMeses] = useState(() => Array(MAXIMO_MESES).fill(''));
  const [factura, cambiarFactura] = useState('');
  const [parametros, cambiarParametros] = useState(null);
  const [errorCarga, cambiarErrorCarga] = useState(null);
  const [intento, cambiarIntento] = useState(0);
  const [estrato, cambiarEstrato] = useState(null);
  const [area, cambiarArea] = useState('');
  const [entradaConsumo, cambiarEntradaConsumo] = useState(null);
  const [entradaPreparada, cambiarEntradaPreparada] = useState(null);
  const [paso, cambiarPaso] = usarRutaSimulacion(entradaPreparada ? 2 : entradaConsumo ? 1 : 0);
  const contenido = useRef(null);
  const pasoAnterior = useRef(paso);

  useEffect(() => {
    const control = new AbortController();
    solicitarJson('simulaciones/parametros-entrada', { signal: control.signal })
      .then((datos) => {
        if (!control.signal.aborted) cambiarParametros(datos);
      })
      .catch((error) => {
        if (!control.signal.aborted) cambiarErrorCarga(error);
      });
    return () => control.abort();
  }, [intento]);

  useEffect(() => {
    if (pasoAnterior.current !== paso) contenido.current.querySelector('h1').focus();
    pasoAnterior.current = paso;
  }, [paso]);

  return (
    <>
      <header className="cabecera">
        <div className="contenedor cabecera-contenido">
          <strong><span className="sol" aria-hidden="true">☀</span> Simulador Solar Montería</strong>
          <small>Estimación gratuita · sin registro · tus datos no se guardan</small>
        </div>
      </header>
      <main className="contenedor" ref={contenido}>
        <nav aria-label="Progreso de la simulación">
          <ol className="pasos">
            {pasos.map((nombre, indice) => (
              <li key={nombre} aria-current={indice === paso ? 'step' : undefined}
                className={indice < paso ? 'completado' : undefined}>
                <span aria-hidden="true">{indice < paso ? '✓' : indice + 1}</span>{nombre}
              </li>
            ))}
          </ol>
        </nav>
        {errorCarga && (
          <div className="aviso" role="alert">
            <MensajeApi error={errorCarga} />
            <button type="button" onClick={() => {
              cambiarErrorCarga(null);
              cambiarIntento(intento + 1);
            }}>Reintentar</button>
          </div>
        )}
        {paso === 0 && (
          <Consumo meses={meses} factura={factura} cambiarMeses={(valores) => {
            cambiarMeses(valores);
            cambiarEntradaConsumo(null);
            cambiarEntradaPreparada(null);
          }} cambiarFactura={(valor) => {
            cambiarFactura(valor);
            cambiarEntradaConsumo(null);
            cambiarEntradaPreparada(null);
          }} parametros={parametros} alContinuar={(datos) => {
              cambiarEntradaConsumo(datos);
              cambiarPaso(1);
            }} />
        )}
        {paso === 1 && (
          <Vivienda estrato={estrato} area={area} cambiarEstrato={(valor) => {
            cambiarEstrato(valor);
            cambiarEntradaPreparada(null);
          }} cambiarArea={(valor) => {
            cambiarArea(valor);
            cambiarEntradaPreparada(null);
          }}
            parametros={parametros} alRegresar={() => cambiarPaso(0)} alContinuar={() => {
              cambiarEntradaPreparada(prepararEntradaSimulacion(entradaConsumo, estrato, area));
              cambiarPaso(2);
            }} />
        )}
        {paso === 2 && entradaPreparada && <SombraPendiente alRegresar={() => cambiarPaso(1)} />}
      </main>
    </>
  );
}

function SombraPendiente({ alRegresar }) {
  return (
    <section className="tarjeta">
      <p className="etapa">Paso 3 de 3</p>
      <h1 tabIndex={-1}>Sombra</h1>
      <p>Este paso está en desarrollo. Puedes regresar para revisar tus datos de consumo y vivienda.</p>
      <footer className="acciones acciones-navegacion">
        <button type="button" className="regresar" onClick={alRegresar}>← Atrás</button>
      </footer>
    </section>
  );
}
