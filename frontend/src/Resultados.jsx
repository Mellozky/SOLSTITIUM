import { prepararResultados } from './resultados.js';

/** Paso 4: muestra los resultados que calculó el back-end, sin repetir ningún cálculo. */
export function Resultados({ resultado, alReiniciar }) {
  const vista = prepararResultados(resultado);
  const { encabezado } = vista;

  return (
    <article className="resultados" aria-labelledby="titulo-resultados">
      <section className={`encabezado-resultado encabezado-${vista.caso}`}>
        <div>
          <span className="insignia-resultado">{encabezado.insignia}</span>
          <h1 id="titulo-resultados" tabIndex={-1}>{encabezado.titulo}</h1>
          <p>{encabezado.texto}</p>
        </div>
        {encabezado.ahorro && (
          <div className="ahorro-destacado">
            <span>Ahorro mensual estimado</span>
            <strong>{encabezado.ahorro.valor}</strong>
            <span>{encabezado.ahorro.detalle}</span>
          </div>
        )}
      </section>

      <ul className="etiquetas-resultado" aria-label="Datos de la estimación">
        {vista.etiquetas.map(({ texto, tipo }) => (
          <li key={texto} className={tipo === 'sombra' ? 'etiqueta-sombra' : undefined}>{texto}</li>
        ))}
      </ul>

      {vista.advertencias.length > 0 && (
        <section className="aviso aviso-resultados" aria-labelledby="titulo-advertencias">
          <h2 id="titulo-advertencias">Ten en cuenta</h2>
          <ul>
            {vista.advertencias.map((advertencia) => <li key={advertencia}>{advertencia}</li>)}
          </ul>
        </section>
      )}

      {vista.tarjetas.length > 0 && (
        <section className="configuraciones" aria-label="Configuraciones evaluadas">
          {vista.tarjetas.map((tarjeta) => <TarjetaConfiguracion key={tarjeta.codigo} {...tarjeta} />)}
        </section>
      )}

      {vista.explicacion && (
        <p className="explicacion-recomendacion">
          <span aria-hidden="true">ⓘ</span>
          <span><strong>{vista.explicacion.titulo}</strong> {vista.explicacion.texto}</span>
        </p>
      )}

      {(vista.gastoMensual || vista.escenarioSinSombra) && (
        <div className="comparaciones">
          {vista.gastoMensual && <GastoMensual {...vista.gastoMensual} />}
          {vista.escenarioSinSombra && <EscenarioSinSombra {...vista.escenarioSinSombra} />}
        </div>
      )}

      <footer className="cierre-resultados">
        <p>Esta es una estimación y no una cotización. No incluye venta de excedentes, degradación de los paneles ni costo de financiación.</p>
        <div className="acciones-resultados">
          <button type="button" className="secundario" onClick={alReiniciar}>Nueva simulación</button>
        </div>
      </footer>
    </article>
  );
}

function TarjetaConfiguracion({ nombre, descripcion, recomendada, indicadores, clasificacion }) {
  return (
    <article className={recomendada ? 'tarjeta-configuracion recomendada' : 'tarjeta-configuracion'}>
      <header>
        <div>
          <h2>{nombre}</h2>
          {descripcion && <small>{descripcion}</small>}
        </div>
        {recomendada && <span className="insignia-recomendada">Recomendada</span>}
      </header>
      <dl className="indicadores">
        {indicadores.map(({ etiqueta, valor }) => (
          <div key={etiqueta}>
            <dt>{etiqueta}</dt>
            <dd>{valor}</dd>
          </div>
        ))}
      </dl>
      <p className="clasificacion"><span>Clasificación</span><strong>{clasificacion}</strong></p>
    </article>
  );
}

function GastoMensual({ filas }) {
  return (
    <section className="tarjeta gasto-mensual" aria-labelledby="titulo-gasto">
      <h2 id="titulo-gasto">Lo que pagarías de energía cada mes</h2>
      <ul>
        {filas.map(({ etiqueta, valor, ancho, tipo }) => (
          <li key={etiqueta}>
            <span>{etiqueta}</span>
            <strong>{valor}</strong>
            <span className={`barra barra-${tipo}`} aria-hidden="true">
              <span style={{ width: `${ancho}%` }} />
            </span>
          </li>
        ))}
      </ul>
      <small>Primer año. El ahorro crece con el incremento anual configurado de la tarifa.</small>
    </section>
  );
}

function EscenarioSinSombra({ ahorro, recuperacion, actual }) {
  return (
    <section className="sin-sombra" aria-labelledby="titulo-sin-sombra">
      <p className="etapa">Dato informativo</p>
      <h2 id="titulo-sin-sombra">Si quitaras la sombra</h2>
      <p>Por ejemplo, podando un árbol. Con el mismo techo:</p>
      <dl className="indicadores-sin-sombra">
        <div><dt>Ahorro mensual</dt><dd>{ahorro}</dd></div>
        <div><dt>Recuperación</dt><dd>{recuperacion}</dd></div>
      </dl>
      {actual && <small>Con la sombra actual: {actual.ahorro} al mes y recuperación en {actual.recuperacion}.</small>}
    </section>
  );
}
