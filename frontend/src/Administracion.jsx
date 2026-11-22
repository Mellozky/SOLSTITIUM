import { useEffect, useState } from 'react';
import { solicitarJson } from './clienteApi.js';
import { MensajeApi } from './MensajeApi.jsx';
import { ContenidoAdministracion } from './ContenidoAdministracion.jsx';

const inicio = '/admin/ingreso';
const secciones = [
  ['tarifas', 'Tarifas y estratos'],
  ['tecnicos', 'Parámetros técnicos'],
  ['sombreado', 'Modelo de sombreado'],
  ['costos', 'Configuraciones y costos'],
  ['paneles', 'Catálogo de paneles'],
  ['rangos', 'Rangos de viabilidad'],
  ['limites', 'Límites de validación'],
  ['bitacora', 'Bitácora'],
];
const rutaSeccion = (seccion) => `/admin/${seccion}`;

async function enviarConCsrf(accion, datos) {
  const { token } = await solicitarJson('autenticacion/csrf');
  return solicitarJson(`autenticacion/${accion}`, { metodo: 'POST', tokenCsrf: token, datos });
}

function Ingreso({ alIngresar }) {
  const [error, cambiarError] = useState(null);
  const [enviando, cambiarEnviando] = useState(false);

  async function ingresar(evento) {
    evento.preventDefault();
    if (enviando) return;
    const datos = Object.fromEntries(new FormData(evento.currentTarget));
    cambiarError(null);
    cambiarEnviando(true);
    try {
      const sesion = await enviarConCsrf('iniciar-sesion', datos);
      alIngresar(sesion);
    } catch (fallo) {
      cambiarError(fallo);
    } finally {
      cambiarEnviando(false);
    }
  }

  return (
    <main className="ingreso-admin">
      <form className="tarjeta" onSubmit={ingresar}>
        <small><span className="sol" aria-hidden="true">☀</span> Simulador Solar Montería</small>
        <h1>Administración de parámetros</h1>
        <label htmlFor="usuario">Usuario</label>
        <input id="usuario" name="usuario" autoComplete="username" required />
        <label htmlFor="contrasena">Contraseña</label>
        <input id="contrasena" name="contrasena" type="password" autoComplete="current-password" required />
        {error && <div className="aviso" role="alert"><MensajeApi error={error} /></div>}
        <button className="principal" disabled={enviando}>{enviando ? 'Ingresando…' : 'Ingresar'}</button>
        <small>Acceso solo para el equipo técnico.</small>
      </form>
    </main>
  );
}

export function Administracion({ ruta }) {
  const [sesion, cambiarSesion] = useState(undefined);
  const [error, cambiarError] = useState(null);
  const [cerrando, cambiarCerrando] = useState(false);
  const [seccion, titulo] = secciones.find(([clave]) => rutaSeccion(clave) === ruta) ?? secciones[0];

  useEffect(() => {
    const control = new AbortController();
    const revalidarPagina = (evento) => {
      if (evento.persisted) window.location.reload();
    };
    window.addEventListener('pageshow', revalidarPagina);
    solicitarJson('autenticacion/sesion', { signal: control.signal })
      .then(cambiarSesion)
      .catch((fallo) => {
        if (control.signal.aborted) return;
        if (fallo.estado === 401) cambiarSesion(null);
        else cambiarError(fallo);
      });
    return () => {
      control.abort();
      window.removeEventListener('pageshow', revalidarPagina);
    };
  }, []);

  useEffect(() => {
    if (sesion === null) window.history.replaceState(null, '', inicio);
    if (sesion) window.history.replaceState(null, '', rutaSeccion(seccion));
  }, [sesion, seccion]);

  async function cerrarSesion() {
    cambiarCerrando(true);
    cambiarError(null);
    try {
      await enviarConCsrf('cerrar-sesion');
      cambiarSesion(null);
    } catch (fallo) {
      if (fallo.estado === 401) cambiarSesion(null);
      else cambiarError(fallo);
    } finally {
      cambiarCerrando(false);
    }
  }

  if (sesion === null) return <Ingreso alIngresar={cambiarSesion} />;
  if (!sesion) return (
    <main className="contenedor">
      {error
        ? <div className="aviso" role="alert"><MensajeApi error={error} /> <a href={ruta}>Reintentar</a></div>
        : <p role="status">Comprobando sesión…</p>}
    </main>
  );

  return (
    <>
      <header className="cabecera-admin">
        <div className="contenedor cabecera-contenido">
          <strong><span className="sol" aria-hidden="true">☀</span> Administración</strong>
          <div><span>{sesion.usuario}</span>
            <button type="button" onClick={cerrarSesion} disabled={cerrando}>Cerrar sesión</button>
          </div>
        </div>
      </header>
      <div className="contenedor distribucion-admin">
        <nav aria-label="Administración" className="tarjeta menu-admin">
          {secciones.map(([clave, nombre]) => (
            <a key={clave} href={rutaSeccion(clave)} aria-current={clave === seccion ? 'page' : undefined}>{nombre}</a>
          ))}
        </nav>
        <main className="contenido-admin">
          <h1>{titulo}</h1>
          {error && <div className="aviso" role="alert"><MensajeApi error={error} /></div>}
          <ContenidoAdministracion seccion={seccion} />
        </main>
      </div>
    </>
  );
}
