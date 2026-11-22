import { CatalogoPaneles } from './CatalogoPaneles.jsx';
import { useEffect, useState } from 'react';
import { solicitarJson } from './clienteApi.js';
import { MensajeApi } from './MensajeApi.jsx';

const numero = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 4 });
const nombres = {
  costo_unitario_kwh: 'Costo unitario del kWh',
  consumo_subsistencia: 'Consumo de subsistencia',
  incremento_anual_tarifa: 'Incremento anual de la tarifa',
  hsp: 'Horas de sol pico',
  relacion_cc_ca: 'Relación CC/CA',
  rango_viabilidad: 'Rango de viabilidad',
  anios_minimo: 'Años mínimos',
  anios_maximo: 'Años máximos',
  franja_horaria: 'Franja horaria',
  porcion_afectada: 'Porción afectada',
  configuracion: 'Configuración',
  tipo_ajuste: 'Tipo de ajuste',
  porcentaje_ajuste: 'Porcentaje de ajuste',
};

function etiqueta(clave) {
  return nombres[clave] ?? (clave[0].toUpperCase() + clave.slice(1)).replaceAll('_', ' ');
}

function presentar(valor) {
  if (valor == null) return '—';
  if (typeof valor === 'number') return numero.format(valor);
  if (typeof valor === 'boolean') return valor ? 'Sí' : 'No';
  if (Array.isArray(valor)) return valor.map(presentar).join(', ');
  return valor;
}

function Tabla({ titulo, columnas, filas, enlace }) {
  return (
    <section className="tarjeta bloque-admin">
      <div className="titulo-bloque">
        <h2>{titulo}</h2>
        {enlace}
      </div>
      {filas.length === 0 ? <p>No hay registros.</p> : (
        <div className="desplazamiento-tabla" role="region" aria-label={titulo} tabIndex={0}>
          <table aria-label={titulo}>
            <thead><tr>{columnas.map((columna) => <th key={columna} scope="col">{columna}</th>)}</tr></thead>
            <tbody>{filas.map(([id, ...celdas]) => (
              <tr key={id}>{celdas.map((celda, indice) => <td key={columnas[indice]}>{celda}</td>)}</tr>
            ))}</tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function limiteDe(datos, registro, campo) {
  return datos.limites.find((limite) => limite.tabla === registro.tabla
    && limite.campo === campo && (limite.clave == null || limite.clave === registro.registroId));
}

function ParametrosGenerales({ datos, titulo }) {
  const filas = datos.registros.filter(({ tabla }) => tabla === 'parametro').map((registro) => {
    const { registroId, valores } = registro;
    const limite = limiteDe(datos, registro, 'valor');
    return [registroId, etiqueta(registroId), `${presentar(valores.valor)} ${valores.unidad ?? ''}`,
      presentar(limite?.valorMinimo), presentar(limite?.valorMaximo),
      valores.fecha_actualizacion?.slice(0, 10) ?? '—'];
  });
  return <Tabla titulo={titulo} columnas={['Parámetro', 'Valor', 'Mínimo', 'Máximo', 'Actualizado']} filas={filas} />;
}

function Bitacora({ registros, reciente = false }) {
  return <Tabla titulo={reciente ? 'Últimos cambios' : 'Historial de cambios'}
    enlace={reciente && <a href="/admin/bitacora">Ver bitácora completa</a>}
    columnas={['Fecha', 'Administrador', 'Cambio', 'Anterior', 'Nuevo']}
    filas={registros.map((registro) => [registro.id, registro.fecha.replace('T', ' '), registro.administrador,
      `${etiqueta(registro.tabla)} · ${etiqueta(registro.registroId)} · ${etiqueta(registro.campo)}`,
      registro.valorAnterior, registro.valorNuevo])} />;
}

function Tarifas({ datos, bitacora }) {
  const tipos = {
    subsidio: ['Subsidio', 'Consumo de subsistencia'],
    contribucion: ['Contribución', 'Todo el consumo'],
  };
  const filas = datos.registros.filter(({ tabla }) => tabla === 'estrato').map((registro) => {
    const { registroId, valores } = registro;
    const [tipo, aplica] = tipos[valores.tipo_ajuste] ?? ['Ninguno', 'No aplica'];
    const limite = limiteDe(datos, registro, 'porcentaje_ajuste');
    return [registroId, registroId, tipo, `${presentar(valores.porcentaje_ajuste)} %`, aplica,
      limite ? `${presentar(limite.valorMinimo)}–${presentar(limite.valorMaximo)} %` : '—'];
  });
  return <>
    <ParametrosGenerales titulo="Tarifa general" datos={datos} />
    <Tabla titulo="Subsidio o contribución por estrato"
      columnas={['Estrato', 'Tipo de ajuste', 'Porcentaje', 'Se aplica sobre', 'Rango permitido']} filas={filas} />
    {bitacora.error
      ? <div role="alert" className="aviso"><MensajeApi error={bitacora.error} /></div>
      : <Bitacora registros={bitacora.registros.slice(0, 3)} reciente />}
  </>;
}

function OtrosParametros({ datos, seccion }) {
  if (seccion === 'limites' || seccion === 'tecnicos') {
    return <ParametrosGenerales datos={datos} titulo={seccion === 'limites' ? 'Límites operativos' : 'Valores técnicos'} />;
  }
  if (seccion === 'rangos') {
    return <Tabla titulo="Clasificaciones" columnas={['Clasificación', 'Años mínimos', 'Años máximos']}
      filas={datos.registros.filter(({ tabla }) => tabla === 'rango_viabilidad').map(({ registroId, valores }) => [
        registroId, valores.clasificacion, presentar(valores.anios_minimo),
        valores.anios_maximo == null ? 'Sin límite' : presentar(valores.anios_maximo),
      ])} />;
  }
  const registros = datos.registros.filter(({ tabla }) => tabla !== 'parametro');
  return <>
    {datos.registros.some(({ tabla }) => tabla === 'parametro') && <ParametrosGenerales datos={datos} titulo="Parámetros generales" />}
    <Tabla titulo="Configuración vigente" columnas={['Registro', 'Campo', 'Valor']}
      filas={registros.flatMap((registro) => Object.entries(registro.valores).map(([campo, valor]) => [
        `${registro.tabla}-${registro.registroId}-${campo}`,
        registro.valores.nombre ?? registro.valores.etiqueta ?? registro.valores.clasificacion
          ?? `${etiqueta(registro.tabla)} ${registro.registroId}`,
        etiqueta(campo), presentar(valor),
      ]))} />
  </>;
}

export function ContenidoAdministracion({ seccion }) {
  const [respuesta, cambiarRespuesta] = useState(null);
  const [error, cambiarError] = useState(null);

  useEffect(() => {
    const control = new AbortController();
    const opciones = { signal: control.signal };
    const clave = seccion === 'limites' ? 'rangos' : seccion;
    const ruta = ['paneles', 'bitacora'].includes(clave) ? `admin/${clave}` : `admin/parametros?seccion=${clave}`;
    const consulta = solicitarJson(ruta, opciones);
    const bitacora = seccion === 'tarifas'
      ? solicitarJson('admin/bitacora', opciones).then((registros) => ({ registros }), (fallo) => ({ error: fallo }))
      : null;
    Promise.all([consulta, bitacora]).then(([datos, historial]) => {
      if (!control.signal.aborted) cambiarRespuesta({ datos, historial });
    }).catch((fallo) => {
      if (!control.signal.aborted) cambiarError(fallo);
    });
    return () => control.abort();
  }, [seccion]);

  if (error) return <div role="alert" className="aviso">
    <MensajeApi error={error} />
    {error.estado === 401 && <a href="/admin/ingreso">Volver a ingresar</a>}
  </div>;
  if (!respuesta) return <p role="status">Cargando…</p>;
  const { datos, historial } = respuesta;
  if (seccion === 'tarifas') return <Tarifas datos={datos} bitacora={historial} />;
  if (seccion === 'paneles') return <CatalogoPaneles inicial={datos} />;
  if (seccion === 'bitacora') return <Bitacora registros={datos} />;
  return <OtrosParametros datos={datos} seccion={seccion} />;
}
