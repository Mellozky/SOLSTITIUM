import { useRef, useState } from 'react';
import { revisarVivienda } from './entrada.js';

const formatoNumero = new Intl.NumberFormat('es-CO');

function descripcionEstrato(estrato, subsistencia) {
  const porcentaje = formatoNumero.format(estrato.porcentajeAjuste);
  switch (estrato.tipoAjuste) {
    case 'SUBSIDIO':
      return `El estrato ${estrato.numero} recibe un subsidio del ${porcentaje} % sobre los primeros ${formatoNumero.format(subsistencia)} kWh del mes. El resto se cobra a tarifa plena.`;
    case 'CONTRIBUCION':
      return `El estrato ${estrato.numero} aporta una contribución del ${porcentaje} % sobre todo el consumo.`;
    default:
      return `El estrato ${estrato.numero} paga la tarifa plena.`;
  }
}

export function Vivienda({ estrato, area, cambiarEstrato, cambiarArea, parametros, alRegresar, alContinuar }) {
  const [intento, cambiarIntento] = useState(false);
  const formulario = useRef(null);
  const errores = revisarVivienda(estrato, area, parametros);
  const seleccionado = parametros.estratos.find(({ numero }) => numero === estrato);
  const errorEstrato = intento && errores.estrato;
  const errorArea = (intento || area.trim()) && errores.area;
  const rango = `${formatoNumero.format(parametros.areaMinima)} a ${formatoNumero.format(parametros.areaMaxima)} m²`;

  function continuar(evento) {
    evento.preventDefault();
    cambiarIntento(true);
    const primerError = Object.keys(errores)[0];
    if (primerError) {
      formulario.current.querySelector(`[name="${primerError}"]`).focus();
      return;
    }
    alContinuar();
  }

  return (
    <form className="tarjeta" ref={formulario} onSubmit={continuar} noValidate>
      <p className="etapa">Paso 2 de 3</p>
      <h1 tabIndex={-1}>Cuéntanos sobre tu vivienda</h1>
      <fieldset className="grupo-estrato" aria-describedby={errorEstrato ? 'ayuda-estrato error-estrato' : 'ayuda-estrato'}>
        <legend>¿De qué estrato es tu vivienda?</legend>
        <small className="ayuda" id="ayuda-estrato">Aparece en la parte superior de tu factura.</small>
        <div className="estratos">
          {parametros.estratos.map(({ numero }) => (
            <label className="opcion-estrato" key={numero}>
              <input type="radio" name="estrato" value={numero} checked={estrato === numero}
                aria-label={`Estrato ${numero}`} aria-invalid={Boolean(errorEstrato)}
                onChange={() => cambiarEstrato(numero)} />
              <span aria-hidden="true">{numero}</span>
            </label>
          ))}
        </div>
        {errorEstrato && <small className="mensaje-campo" id="error-estrato">{errorEstrato}</small>}
      </fieldset>
      {seleccionado && (
        <p className="nota-estrato" role="status">
          <span aria-hidden="true">ⓘ</span>
          {descripcionEstrato(seleccionado, parametros.consumoSubsistencia)}
        </p>
      )}
      <section className="seccion-techo">
        <div>
          <label htmlFor="area" className="etiqueta-area">¿Cuánta área libre tiene tu techo?</label>
          <p className="ayuda" id="ayuda-area">Mide solo la parte libre. Descuenta tanques, antenas, claraboyas y cualquier otro obstáculo. Para una zona rectangular, multiplica el largo por el ancho.</p>
          <div className="entrada entrada-area">
            <input id="area" name="area" inputMode="decimal" value={area} aria-invalid={Boolean(errorArea)}
              aria-describedby={errorArea ? 'ayuda-area rango-area error-area' : 'ayuda-area rango-area'}
              onChange={(evento) => cambiarArea(evento.target.value)} />
            <span aria-hidden="true">m²</span>
          </div>
          <small className="ayuda" id="rango-area">Rango permitido: {rango}.</small>
          {errorArea && <small className="mensaje-campo" id="error-area">{errorArea}</small>}
        </div>
        <figure className="ejemplo-techo" aria-hidden="true">
          <div className="plano-techo">
            <strong>Área libre</strong>
            <div className="obstaculos">
              <span>Tanque</span><span>Antena</span>
            </div>
          </div>
          <figcaption>La guía muestra cómo descontar los obstáculos.</figcaption>
        </figure>
      </section>
      <footer className="acciones acciones-navegacion">
        <button type="button" className="regresar" onClick={alRegresar}>← Atrás</button>
        <button className="principal">Siguiente: sombra <span aria-hidden="true">→</span></button>
      </footer>
    </form>
  );
}
