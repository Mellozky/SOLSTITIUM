import { useEffect, useRef, useState } from 'react';
import { Consumo } from './Consumo.jsx';
import { Vivienda } from './Vivienda.jsx';
import { Sombra } from './Sombra.jsx';
import { Resultados } from './Resultados.jsx';
import { MAXIMO_MESES, prepararEntradaSimulacion } from './entrada.js';
import { usarRutaSimulacion } from './rutasSimulacion.js';
import { solicitarJson } from './clienteApi.js';
import { MensajeApi } from './MensajeApi.jsx';
import { MOSTRAR_AVISO_DATOS_PRUEBA, TEXTO_AVISO_DATOS_PRUEBA, TITULO_AVISO_DATOS_PRUEBA } from './avisoDatosPrueba.js';
import { debeConfirmarSalida, RUTA_ADMINISTRACION } from './accesoAdministracion.js';

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
  const [seleccionesSombra, cambiarSeleccionesSombra] = useState({});
  const [resultado, cambiarResultado] = useState(null);
  const [paso, cambiarPaso] = usarRutaSimulacion(
    resultado ? 3 : entradaPreparada ? 2 : entradaConsumo ? 1 : 0);
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

  async function simular(respuestasSombra, signal) {
    const respuesta = await solicitarJson('simulaciones', {
      datos: { ...entradaPreparada, respuestasSombra },
      signal,
    });
    if (signal.aborted) return;
    cambiarResultado(respuesta);
    cambiarPaso(3);
  }

  // Cualquier cambio en los datos invalida la entrada preparada y el resultado anterior.
  function descartarCalculos() {
    cambiarEntradaPreparada(null);
    cambiarResultado(null);
  }

  function reiniciar() {
    cambiarMeses(Array(MAXIMO_MESES).fill(''));
    cambiarFactura('');
    cambiarEstrato(null);
    cambiarArea('');
    cambiarSeleccionesSombra({});
    cambiarEntradaConsumo(null);
    descartarCalculos();
    cambiarPaso(0);
  }

  useEffect(() => {
    if (pasoAnterior.current !== paso) contenido.current.querySelector('h1').focus();
    pasoAnterior.current = paso;
  }, [paso]);

  return (
    <>
      <header className="cabecera">
        <div className="contenedor cabecera-contenido">
          <strong><span className="sol" aria-hidden="true">☀</span> Simulador Solar Montería</strong>
          <div className="cabecera-lateral">
            <small>Estimación gratuita · sin registro · tus datos no se guardan</small>
            <a className="enlace-administracion" href={RUTA_ADMINISTRACION} onClick={(evento) => {
              const datos = { meses, factura, estrato, area, seleccionesSombra };
              if (!debeConfirmarSalida(datos, (mensaje) => window.confirm(mensaje))) evento.preventDefault();
            }}>Administración</a>
          </div>
        </div>
      </header>
      {MOSTRAR_AVISO_DATOS_PRUEBA && (
        <aside className="aviso-demostracion" aria-label={TITULO_AVISO_DATOS_PRUEBA}>
          <p className="contenedor"><strong>{TITULO_AVISO_DATOS_PRUEBA}.</strong> {TEXTO_AVISO_DATOS_PRUEBA}</p>
        </aside>
      )}
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
            descartarCalculos();
          }} cambiarFactura={(valor) => {
            cambiarFactura(valor);
            cambiarEntradaConsumo(null);
            descartarCalculos();
          }} parametros={parametros} alContinuar={(datos) => {
              cambiarEntradaConsumo(datos);
              cambiarPaso(1);
            }} />
        )}
        {paso === 1 && (
          <Vivienda estrato={estrato} area={area} cambiarEstrato={(valor) => {
            cambiarEstrato(valor);
            descartarCalculos();
          }} cambiarArea={(valor) => {
            cambiarArea(valor);
            descartarCalculos();
          }}
            parametros={parametros} alRegresar={() => cambiarPaso(0)} alContinuar={() => {
              cambiarEntradaPreparada(prepararEntradaSimulacion(entradaConsumo, estrato, area));
              cambiarPaso(2);
            }} />
        )}
        {paso === 2 && entradaPreparada && (
          <Sombra franjas={parametros.franjas} porciones={parametros.porciones}
            selecciones={seleccionesSombra} alElegir={(franjaId, porcionId) => {
              cambiarSeleccionesSombra((selecciones) => ({ ...selecciones, [franjaId]: porcionId }));
              cambiarResultado(null);
            }}
            alRegresar={() => cambiarPaso(1)} alSimular={simular} />
        )}
        {paso === 3 && resultado && <Resultados resultado={resultado} alReiniciar={reiniciar} />}
      </main>
    </>
  );
}
