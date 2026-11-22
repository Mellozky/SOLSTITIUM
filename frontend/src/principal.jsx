import { createRoot } from 'react-dom/client';
import { Simulador } from './Simulador.jsx';
import { Administracion } from './Administracion.jsx';
import './estilos.css';
import './administracion.css';

const ruta = window.location.pathname;
createRoot(document.getElementById('aplicacion')).render(
  ruta === '/admin' || ruta.startsWith('/admin/')
    ? <Administracion ruta={ruta} />
    : <Simulador />
);
