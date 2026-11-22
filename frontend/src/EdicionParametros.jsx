import { useRef, useState } from 'react';
import { solicitarJson } from './clienteApi.js';
import { MensajeApi } from './MensajeApi.jsx';
import { camposDe, claveCampo, esEditable, prepararCambios, valorDeEntrada } from './edicionParametros.js';
import './edicionParametros.css';

const formato = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 6 });
const etiquetas = {
  costo_unitario_kwh: 'Costo unitario del kWh', consumo_subsistencia: 'Consumo de subsistencia',
  incremento_anual_tarifa: 'Incremento anual de la tarifa', hsp: 'Horas de sol pico',
  relacion_cc_ca: 'Relación CC/CA', costo_kwp: 'Costo por kWp', anios_minimo: 'Años mínimos',
  anios_maximo: 'Años máximos', coeficiente_recuperacion: 'Coeficiente de recuperación',
  porcentaje_ajuste: 'Porcentaje de ajuste', amplificacion_serie: 'Amplificación en serie',
  configuraciones: 'Configuraciones evaluadas', muestra_advertencia: 'Mostrar advertencia',
  area_minima: 'Área mínima', area_maxima: 'Área máxima', umbral_diferencia_factura: 'Umbral de diferencia de factura',
};
const grupos = {
  parametro: 'Parámetros generales', estrato: 'Subsidio o contribución por estrato', franja_horaria: 'Franjas horarias',
  porcion_afectada: 'Porciones afectadas', configuracion: 'Configuraciones', tramo_sombreado: 'Tramos de sombreado',
  rango_viabilidad: 'Clasificaciones de viabilidad',
};
const tiposAjuste = {
  subsidio: 'Subsidio · consumo de subsistencia',
  contribucion: 'Contribución · todo el consumo',
};

function etiqueta(clave) {
  return etiquetas[clave] ?? (clave[0].toUpperCase() + clave.slice(1)).replaceAll('_', ' ');
}

function mostrar(valor) {
  return typeof valor === 'number' ? formato.format(valor) : valor ?? '—';
}

function mostrarLimite(campo, valor) {
  if (!campo.startsWith('hora_') || valor == null) return mostrar(valor);
  return [Math.floor(valor / 60), valor % 60].map((parte) => String(parte).padStart(2, '0')).join(':');
}

function nombreRegistro(registro) {
  const { tabla, registroId, valores } = registro;
  if (tabla === 'parametro') return etiqueta(registroId);
  if (tabla === 'estrato') return `Estrato ${registroId}`;
  return valores.nombre ?? valores.etiqueta ?? valores.clasificacion ?? registroId;
}

function Control({ id, campo, valor, cambiar, opciones, error, admiteNulo, nombre }) {
  const propiedades = { id, 'aria-invalid': Boolean(error), 'aria-describedby': error ? `${id}-error` : undefined };
  if (campo === 'configuraciones') {
    return <div role="group" aria-label={nombre}>
      {opciones.map(({ registroId, valores }) => {
        const identificador = Number(registroId);
        const marcado = valor.includes(identificador);
        return <label key={registroId}>
          <input type="checkbox" checked={marcado} onChange={() => cambiar(marcado
            ? valor.filter((actual) => actual !== identificador) : [...valor, identificador])} />
          {' '}{valores.nombre ?? valores.codigo}
        </label>;
      })}
    </div>;
  }
  if (typeof valor === 'boolean') {
    return <input {...propiedades} type="checkbox" checked={valor}
      onChange={(evento) => cambiar(evento.target.checked)} />;
  }
  return <input {...propiedades} type={campo.startsWith('hora_') ? 'time' : 'text'}
    inputMode={campo.startsWith('hora_') ? undefined : 'decimal'} value={valor}
    placeholder={admiteNulo ? 'Sin límite' : undefined} onChange={(evento) => cambiar(evento.target.value)} />;
}

export function EdicionParametros({ datos, seccion, alGuardar }) {
  const [vigentes, cambiarVigentes] = useState(datos);
  const [borrador, cambiarBorrador] = useState({});
  const [erroresCampos, cambiarErroresCampos] = useState({});
  const [errorApi, cambiarErrorApi] = useState(null);
  const [mensaje, cambiarMensaje] = useState('');
  const [pendienteLectura, cambiarPendienteLectura] = useState(false);
  const [ocupado, cambiarOcupado] = useState(false);
  const operacion = useRef(false);
  const gruposPresentes = vigentes.registros.reduce((porTabla, registro) => {
    (porTabla[registro.tabla] ??= []).push(registro);
    return porTabla;
  }, {});
  const configuraciones = gruposPresentes.configuracion ?? [];
  const ultimoOrden = Math.max(...(gruposPresentes.rango_viabilidad ?? []).map(({ valores }) => valores.orden));

  async function recargar() {
    try {
      const registrados = await solicitarJson(`admin/parametros?seccion=${seccion}`);
      cambiarVigentes(registrados);
      cambiarBorrador({});
      cambiarPendienteLectura(false);
      cambiarErrorApi(null);
      await alGuardar?.();
    } catch (error) {
      cambiarErrorApi(error);
    }
  }

  async function guardar(evento) {
    evento.preventDefault();
    if (operacion.current || pendienteLectura) return;
    const { cambios, errores } = prepararCambios(vigentes.registros, seccion, borrador);
    cambiarErroresCampos(errores);
    cambiarErrorApi(null);
    cambiarMensaje('');
    if (Object.keys(errores).length) return;
    if (!cambios.length) {
      cambiarMensaje('No hay cambios para guardar.');
      return;
    }
    operacion.current = true;
    cambiarOcupado(true);
    try {
      const { token } = await solicitarJson('autenticacion/csrf');
      const { modificados } = await solicitarJson('admin/parametros', {
        metodo: 'PUT', tokenCsrf: token, datos: { cambios },
      });
      cambiarMensaje(modificados > 0 ? 'Cambios guardados.' : 'No hubo cambios en los valores registrados.');
      cambiarPendienteLectura(true);
      await recargar();
    } catch (error) {
      cambiarErrorApi(error);
    } finally {
      operacion.current = false;
      cambiarOcupado(false);
    }
  }

  async function reintentarLectura() {
    if (operacion.current) return;
    operacion.current = true;
    cambiarOcupado(true);
    try {
      await recargar();
    } finally {
      operacion.current = false;
      cambiarOcupado(false);
    }
  }

  return <form className="edicion-parametros" onSubmit={guardar} noValidate>
    <div className="acciones-edicion-parametros">
      <button className="principal" disabled={ocupado || pendienteLectura || !Object.keys(borrador).length}>
        {ocupado ? 'Procesando…' : 'Guardar cambios'}
      </button>
      <button type="button" className="regresar" disabled={ocupado || pendienteLectura || !Object.keys(borrador).length}
        onClick={() => {
          cambiarErroresCampos({});
          cambiarBorrador({});
          cambiarErrorApi(null);
          cambiarMensaje('');
        }}>Cancelar</button>
    </div>
    <p>Escribe los números sin separadores de miles. Puedes usar coma o punto para los decimales.</p>
    {mensaje && <p role="status">{mensaje}</p>}
    {errorApi && <div className="aviso" role="alert">
      {pendienteLectura && <p>El guardado terminó. Falta volver a consultar los valores registrados.</p>}
      <MensajeApi error={errorApi} />
      {pendienteLectura && <button type="button" disabled={ocupado} onClick={reintentarLectura}>Volver a consultar</button>}
    </div>}
    {Object.keys(erroresCampos).length > 0 && <p role="alert">Revisa los campos señalados antes de guardar.</p>}
    <fieldset disabled={ocupado || pendienteLectura}>
      <legend className="solo-lectores">Valores y límites de los parámetros</legend>
      {Object.entries(gruposPresentes).map(([tabla, registros]) => (
        <section className="tarjeta bloque-admin" key={tabla}>
          <h2>{seccion === 'tarifas' && tabla === 'parametro' ? 'Tarifa general' : grupos[tabla]}</h2>
          <div className="desplazamiento-tabla" role="region" aria-label={grupos[tabla]} tabIndex={0}>
            <table>
              <thead><tr><th scope="col">Parámetro</th><th scope="col">Registrado</th>
                <th scope="col">Editar valor</th><th scope="col">Mínimo</th><th scope="col">Máximo</th></tr></thead>
              <tbody>{registros.flatMap((registro) => camposDe(registro, seccion).map((campo) => {
                const clave = claveCampo(registro, campo);
                const id = `parametro-${tabla}-${registro.registroId}-${campo}`;
                const valor = registro.valores[campo];
                const editable = esEditable(registro, campo);
                const nombre = nombreRegistro(registro);
                const titulo = campo === 'valor' ? nombre : `${nombre} · ${etiqueta(campo)}`;
                const limite = vigentes.limites.find((limite) => limite.tabla === tabla && limite.campo === campo
                  && (limite.clave == null || limite.clave === registro.registroId));
                const admiteNulo = campo === 'anios_maximo' && registro.valores.orden === ultimoOrden;
                const valorRegistrado = campo === 'configuraciones'
                  ? valor.map((id) => configuraciones.find(({ registroId }) => Number(registroId) === id)?.valores.nombre ?? id).join(', ')
                  : typeof valor === 'boolean' ? (valor ? 'Sí' : 'No')
                    : valor == null && admiteNulo ? 'Sin límite'
                      : mostrar(campo.startsWith('hora_') ? valorDeEntrada(campo, valor) : valor);
                return <tr key={clave}>
                  <th scope="row">
                    <label htmlFor={editable && campo !== 'configuraciones' ? id : undefined}>{titulo}</label>
                    {tabla === 'estrato' && <small>{tiposAjuste[registro.valores.tipo_ajuste] ?? 'Sin ajuste'} · solo lectura</small>}
                  </th>
                  <td>{valorRegistrado} {registro.valores.unidad}
                    {registro.valores.fecha_actualizacion && <small>{registro.valores.fecha_actualizacion.slice(0, 10)}</small>}</td>
                  <td>{editable ? <Control id={id} campo={campo} nombre={titulo}
                    valor={borrador[clave] ?? valorDeEntrada(campo, valor)} admiteNulo={admiteNulo}
                    opciones={configuraciones} error={erroresCampos[clave]}
                    cambiar={(nuevo) => cambiarBorrador((actual) => ({ ...actual, [clave]: nuevo }))} />
                    : 'Solo lectura'}
                  {erroresCampos[clave] && <small id={`${id}-error`}>{erroresCampos[clave]}</small>}</td>
                  <td>{mostrarLimite(campo, limite?.valorMinimo)}</td><td>{mostrarLimite(campo, limite?.valorMaximo)}</td>
                </tr>;
              }))}</tbody>
            </table>
          </div>
        </section>
      ))}
    </fieldset>
  </form>;
}
