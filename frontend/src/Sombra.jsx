import { useEffect, useMemo, useRef, useState } from 'react';
import { solicitarJson } from './clienteApi.js';
import { MensajeApi } from './MensajeApi.jsx';

export function Sombra({ franjas, porciones, selecciones, alElegir, alRegresar, alSimular }) {
  const [validar, cambiarValidar] = useState(false);
  const [previa, cambiarPrevia] = useState(null);
  const [intentoPrevia, cambiarIntentoPrevia] = useState(0);
  const [enviando, cambiarEnviando] = useState(false);
  const [errorEnvio, cambiarErrorEnvio] = useState(null);
  const formulario = useRef(null);
  const envio = useRef(null);
  const faltantes = franjas.filter(({ id }) => !porciones.some((porcion) => porcion.id === selecciones[id]));
  const respuestasSombra = useMemo(() => franjas.map(({ id }) => ({
    franjaId: id,
    porcionId: selecciones[id],
  })), [franjas, selecciones]);
  const completa = faltantes.length === 0;
  const previaActual = previa?.respuestas === respuestasSombra ? previa : null;

  useEffect(() => {
    if (!completa) return;
    const control = new AbortController();
    solicitarJson('simulaciones/vista-previa-sombra', {
      datos: { respuestasSombra },
      signal: control.signal,
    }).then(({ tramo }) => {
      if (!control.signal.aborted) cambiarPrevia({ respuestas: respuestasSombra, tramo });
    }).catch((error) => {
      if (!control.signal.aborted) cambiarPrevia({ respuestas: respuestasSombra, error });
    });
    return () => control.abort();
  }, [completa, respuestasSombra, intentoPrevia]);

  useEffect(() => () => envio.current?.abort(), []);

  async function continuar(evento) {
    evento.preventDefault();
    if (!completa) {
      cambiarValidar(true);
      formulario.current.querySelector(`[name="franja-${faltantes[0].id}"]`)?.focus();
      return;
    }
    if (!previaActual?.tramo || envio.current) return;

    const control = new AbortController();
    envio.current = control;
    cambiarEnviando(true);
    cambiarErrorEnvio(null);
    try {
      await alSimular(respuestasSombra, control.signal);
    } catch (error) {
      if (!control.signal.aborted) cambiarErrorEnvio(error);
    } finally {
      envio.current = null;
      if (!control.signal.aborted) cambiarEnviando(false);
    }
  }

  return (
    <form ref={formulario} onSubmit={continuar} noValidate>
      <p className="etapa">Paso 3 de 3</p>
      <h1 tabIndex={-1}>¿Le da sombra a tu techo?</h1>
      <p className="introduccion-sombra">Piensa en árboles, edificios vecinos, muros o tanques. Para cada momento del día, elige el dibujo que más se parezca a tu techo.</p>

      {franjas.map(({ id, nombre, horaInicio, horaFin }, indice) => {
        const error = validar && faltantes.some((franja) => franja.id === id);
        const horario = `horario-${id}`;
        const mensaje = `error-sombra-${id}`;
        return (
          <div key={id} className="tarjeta fila-sombra" role="radiogroup"
            aria-labelledby={`titulo-franja-${id}`}
            aria-describedby={error ? `${horario} ${mensaje}` : horario}
            aria-invalid={error} aria-required="true">
            <div className="franja-sombra">
              <span className={`sol-franja sol-franja-${indice}`} aria-hidden="true">☀</span>
              <div>
                <h2 id={`titulo-franja-${id}`}>{nombre}</h2>
                <small id={horario}>{horaInicio.slice(0, 5)}–{horaFin.slice(0, 5)}</small>
              </div>
            </div>
            <div className="opciones-sombra">
              {porciones.map(({ id: porcionId, etiqueta }) => (
                <label className="opcion-sombra" key={porcionId}>
                  <input type="radio" name={`franja-${id}`} value={porcionId}
                    checked={selecciones[id] === porcionId} disabled={enviando}
                    onChange={() => {
                      cambiarErrorEnvio(null);
                      alElegir(id, porcionId);
                    }} />
                  <span className="contenido-opcion-sombra">
                    <span className="dibujo-sombra" aria-hidden="true" />
                    {etiqueta}
                  </span>
                </label>
              ))}
            </div>
            {error && <small id={mensaje} className="mensaje-campo error-franja" role="alert">
              Elige una opción para esta franja.
            </small>}
          </div>
        );
      })}

      <div className="tarjeta cierre-sombra">
        <div aria-live="polite">
          <div>Nivel de sombra estimado</div>
          {previaActual?.tramo && <strong>{previaActual.tramo}</strong>}
          {!completa && <small>Responde todas las franjas para verlo.</small>}
          {completa && !previaActual && <small>Consultando el nivel de sombra…</small>}
          {previaActual?.error && (
            <>
              <MensajeApi error={previaActual.error} />
              <button type="button" className="regresar" onClick={() => {
                cambiarPrevia(null);
                cambiarIntentoPrevia((valor) => valor + 1);
              }}>Reintentar vista previa</button>
            </>
          )}
        </div>
        <div className="botones-sombra">
          <button type="button" className="regresar" disabled={enviando} onClick={alRegresar}>← Atrás</button>
          <button className="principal" disabled={enviando || (completa && !previaActual?.tramo)}>
            {enviando ? 'Calculando…' : 'Ver resultados'} <span aria-hidden="true">→</span>
          </button>
        </div>
        {errorEnvio && <div className="aviso error-envio-sombra" role="alert"><MensajeApi error={errorEnvio} /></div>}
      </div>
    </form>
  );
}
