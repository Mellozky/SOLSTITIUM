import { useState } from 'react';
import { solicitarJson } from './clienteApi.js';
import { MensajeApi } from './MensajeApi.jsx';
import './catalogoPaneles.css';

const ruta = 'admin/paneles';
const numero = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 3 });
const campos = [
  { nombre: 'marca', titulo: 'Marca' },
  { nombre: 'modelo', titulo: 'Modelo' },
  { nombre: 'potenciaWp', titulo: 'Potencia (Wp)', limite: 'potencia_wp', paso: '0.01' },
  { nombre: 'areaM2', titulo: 'Área (m²)', limite: 'area_m2', paso: '0.001' },
];

export function CatalogoPaneles({ inicial }) {
  const [catalogo, cambiarCatalogo] = useState(inicial);
  const [edicion, editar] = useState(null);
  const [ocupado, ocupar] = useState(false);
  const [desactualizado, marcarDesactualizado] = useState(false);
  const [error, mostrarError] = useState(null);
  const [mensaje, informar] = useState('');
  const bloqueado = ocupado || desactualizado;

  async function actualizar() {
    try {
      cambiarCatalogo(await solicitarJson(ruta));
      marcarDesactualizado(false);
      mostrarError(null);
    } catch (fallo) {
      marcarDesactualizado(true);
      mostrarError(fallo);
    }
  }

  async function guardar(destino, metodo, datos) {
    if (bloqueado) return;
    ocupar(true);
    mostrarError(null);
    informar('');
    try {
      const { token } = await solicitarJson('autenticacion/csrf');
      await solicitarJson(destino, { metodo, datos, tokenCsrf: token });
      editar(null);
      informar('Cambio guardado.');
      await actualizar();
    } catch (fallo) {
      mostrarError(fallo);
    } finally {
      ocupar(false);
    }
  }

  function abrir(panel) {
    mostrarError(null);
    informar('');
    editar(panel);
  }

  function enviar(evento) {
    evento.preventDefault();
    const datos = Object.fromEntries(new FormData(evento.currentTarget));
    datos.marca = datos.marca.trim();
    datos.modelo = datos.modelo.trim();
    datos.potenciaWp = Number(datos.potenciaWp);
    datos.areaM2 = Number(datos.areaM2);
    return edicion.id
      ? guardar(`${ruta}/${edicion.id}`, 'PUT', datos)
      : guardar(ruta, 'POST', datos);
  }

  function designar(panel) {
    if (window.confirm(`¿Designar ${panel.marca} ${panel.modelo} como panel de referencia para las próximas simulaciones?`)) {
      guardar(`${ruta}/${panel.id}/referencia`, 'PUT', { confirmado: true });
    }
  }

  return <section className="catalogo-paneles" aria-label="Gestión del catálogo">
    <div className="barra-catalogo">
      <p>Selecciona el panel de referencia para las próximas simulaciones.</p>
      <button type="button" disabled={bloqueado || edicion !== null} onClick={() => abrir({})}>+ Agregar panel</button>
    </div>
    <div role="status">{mensaje}</div>
    {error && <div className="aviso" role="alert">
      {desactualizado && <strong>El cambio se guardó. Falta actualizar la lista.</strong>}
      <MensajeApi error={error} />
      {error.estado === 401 && <a href="/admin/ingreso">Volver a ingresar</a>}
      {desactualizado && <button type="button" disabled={ocupado} onClick={async () => {
        ocupar(true);
        try { await actualizar(); } finally { ocupar(false); }
      }}>Actualizar lista</button>}
    </div>}
    {edicion && <form className="tarjeta bloque-admin" onSubmit={enviar} key={edicion.id ?? 'nuevo'}>
      <h2>{edicion.id ? 'Editar panel' : 'Agregar panel'}</h2>
      <fieldset disabled={ocupado}>
        {campos.map(({ nombre, titulo, limite, paso }) => {
          const rango = catalogo.limites.find(({ campo }) => campo === limite);
          return <label key={nombre}>{titulo}
            <input name={nombre} type={limite ? 'number' : 'text'} required
              autoFocus={nombre === 'marca'} defaultValue={edicion[nombre] ?? ''}
              maxLength={limite ? undefined : 60} step={paso}
              min={rango?.valorMinimo} max={rango?.valorMaximo} />
            {rango && <small>De {numero.format(rango.valorMinimo)} a {numero.format(rango.valorMaximo)}</small>}
          </label>;
        })}
        <div className="acciones-panel">
          <button className="principal" type="submit">Guardar panel</button>
          <button type="button" onClick={() => abrir(null)}>Cancelar</button>
        </div>
      </fieldset>
    </form>}
    <div className="tarjeta bloque-admin desplazamiento-tabla" role="region" aria-label="Paneles registrados" tabIndex={0}>
      <table aria-label="Catálogo de paneles">
        <thead><tr>{['Referencia', 'Marca', 'Modelo', 'Potencia', 'Área', 'Estado', 'Acciones'].map(t => <th scope="col" key={t}>{t}</th>)}</tr></thead>
        <tbody>{catalogo.paneles.map(panel => <tr key={panel.id}>
          <td><label><input type="radio" name="referencia" checked={panel.esReferencia}
            aria-label={`Referencia: ${panel.marca} ${panel.modelo}`}
            disabled={bloqueado || !!edicion || !panel.activo}
            onChange={() => designar(panel)} /> {panel.esReferencia && 'Referencia'}</label></td>
          <td>{panel.marca}</td><td>{panel.modelo}</td>
          <td>{numero.format(panel.potenciaWp)} Wp</td><td>{numero.format(panel.areaM2)} m²</td>
          <td>{panel.activo ? 'Activo' : 'Inactivo'}</td>
          <td><div className="acciones-panel">
            <button type="button" disabled={bloqueado || !!edicion} onClick={() => abrir(panel)}>Editar</button>
            <button type="button" disabled={bloqueado || !!edicion || panel.esReferencia}
              onClick={() => guardar(`${ruta}/${panel.id}/estado`, 'PUT', { activo: !panel.activo })}>
              {panel.activo ? 'Desactivar' : 'Activar'}
            </button>
          </div></td>
        </tr>)}</tbody>
      </table>
      {!catalogo.paneles.length && <p>No hay paneles registrados.</p>}
    </div>
  </section>;
}
