import { useEffect, useRef, useState } from 'react';
import { prepararConsumo, revisarConsumo } from './entrada.js';

const formatoPromedio = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });

function PromedioConsumo({ consulta, cantidad }) {
  const [resultado, cambiarResultado] = useState(null);

  useEffect(() => {
    if (!consulta) return;
    const control = new AbortController();
    const espera = setTimeout(async () => {
      try {
        const respuesta = await fetch('/api/simulaciones/promedio-consumo', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: consulta,
          signal: control.signal,
        });
        if (!respuesta.ok) throw new Error();
        const valor = await respuesta.json();
        if (!control.signal.aborted) cambiarResultado({ consulta, valor });
      } catch {
        if (!control.signal.aborted) cambiarResultado({ consulta, error: true });
      }
    }, 350);
    return () => {
      clearTimeout(espera);
      control.abort();
    };
  }, [consulta]);

  const actual = consulta && resultado?.consulta === consulta ? resultado : null;
  return (
    <div className="resumen">
      <div aria-live="polite">
        <div>Promedio mensual{consulta && ` (${cantidad} ${cantidad === 1 ? 'mes ingresado' : 'meses ingresados'})`}</div>
        {actual?.error
          ? <small>No se pudo consultar el promedio.</small>
          : <strong>{actual ? `${formatoPromedio.format(actual.valor)} kWh` : '—'}</strong>}
      </div>
      <small>Ingresa consumos consecutivos, empezando por el mes más reciente.</small>
    </div>
  );
}

export function Consumo({ meses, factura, cambiarMeses, cambiarFactura, parametros, alContinuar }) {
  const [intento, cambiarIntento] = useState(false);
  const formulario = useRef(null);
  const { errores, advertencias, mesesIngresados } = revisarConsumo(meses, factura, parametros ?? {});
  const consulta = parametros && !Object.keys(errores).some((campo) => campo !== 'factura')
    ? JSON.stringify(prepararConsumo(mesesIngresados, ''))
    : null;

  function continuar(evento) {
    evento.preventDefault();
    if (!parametros) return;
    cambiarIntento(true);
    const primerError = Object.keys(errores)[0];
    if (primerError !== undefined) {
      formulario.current.elements.namedItem(primerError).focus();
      return;
    }
    alContinuar(prepararConsumo(mesesIngresados, factura));
  }

  return (
    <div className="distribucion-consumo">
      <form className="tarjeta" ref={formulario} onSubmit={continuar} noValidate>
        <p className="etapa">Paso 1 de 3</p>
        <h1>¿Cuánta energía consume tu hogar?</h1>
        <p>Copia de tu factura el consumo en kWh de los últimos meses. Con un mes basta. Con más meses, el promedio es más representativo.</p>
        <div className="meses">
          {meses.map((valor, indice) => {
            const error = (intento || valor.trim()) && errores[indice];
            const mensaje = error || advertencias[indice];
            return (
              <div key={indice}>
                <label htmlFor={`mes-${indice}`}>Mes {indice + 1}{indice === 0 && ' (más reciente)'}</label>
                <div className="entrada">
                  <input id={`mes-${indice}`} name={indice} inputMode="decimal" value={valor}
                    placeholder="—" aria-invalid={Boolean(error)}
                    aria-describedby={mensaje ? `mensaje-mes-${indice}` : undefined}
                    onChange={(evento) => cambiarMeses(meses.map((mes, posicion) =>
                      posicion === indice ? evento.target.value : mes))} />
                  <span aria-hidden="true">kWh</span>
                </div>
                {mensaje && <small className="mensaje-campo" id={`mensaje-mes-${indice}`}>{mensaje}</small>}
              </div>
            );
          })}
        </div>
        <PromedioConsumo consulta={consulta} cantidad={mesesIngresados.length} />
        <label htmlFor="factura">Valor de tu última factura</label>
        <small className="ayuda" id="ayuda-factura">Opcional. Escribe el valor del mes más reciente sin separadores de miles.</small>
        <div className="entrada corta">
          <span aria-hidden="true">$</span>
          <input id="factura" name="factura" inputMode="decimal" value={factura}
            aria-invalid={Boolean(errores.factura)}
            aria-describedby={errores.factura ? 'ayuda-factura error-factura' : 'ayuda-factura'}
            onChange={(evento) => cambiarFactura(evento.target.value)} />
          <span aria-hidden="true">COP</span>
        </div>
        {errores.factura && <small className="mensaje-campo" id="error-factura">{errores.factura}</small>}
        <footer className="acciones">
          <button className="principal" disabled={!parametros}>Siguiente: tu vivienda <span aria-hidden="true">→</span></button>
        </footer>
      </form>
      <aside className="tarjeta guia">
        <h2>¿Dónde está el consumo en mi factura?</h2>
        <div className="ejemplo-factura" aria-hidden="true">
          <div className="lineas-factura"><i /><i /><i /></div>
          <div className="dato-factura"><strong>Consumo (kWh)</strong><strong>259</strong></div>
          <div className="barras-factura"><i /><i /><i /><i /><i /><i /></div>
        </div>
        <p>Busca la casilla <strong>Consumo</strong> o <strong>Energía activa</strong>, expresada en <strong>kWh</strong>.</p>
        <p>No pedimos nombre, cédula, dirección ni número de contrato.</p>
      </aside>
    </div>
  );
}
