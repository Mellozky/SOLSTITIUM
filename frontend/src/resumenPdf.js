// CU-05: el navegador genera y descarga el resumen en PDF con los mismos datos de la pantalla.
// No hay cálculos aquí: el contenido sale de prepararResultados, igual que la vista de resultados.
import { prepararResultados } from './resultados.js';

const MARGEN = 18;
const PUNTO_A_MM = 0.3528;
const VIOLETA = [107, 43, 255];
const VIOLETA_CLARO = [184, 154, 255];
const TAUPE = [139, 126, 113];
const TEXTO = [46, 41, 36];
const PORCELANA = [228, 227, 222];
const BLANCO = [255, 255, 255];

const formatoGeneracion = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'long', timeStyle: 'short', timeZone: 'America/Bogota',
});

export function nombreArchivo(fecha = new Date()) {
  const dia = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(fecha);
  return `simulacion-solar-monteria-${dia}.pdf`;
}

/** Escribe texto con salto de línea y de página, siguiendo una posición vertical. */
function crearEscritor(doc) {
  const ancho = doc.internal.pageSize.getWidth();
  const alto = doc.internal.pageSize.getHeight();
  const util = ancho - 2 * MARGEN;
  let y = MARGEN;

  function asegurar(espacio) {
    if (y + espacio > alto - MARGEN - 8) {
      doc.addPage();
      y = MARGEN;
    }
  }

  function texto(contenido, { tamano = 10.5, negrita = false, color = TEXTO, x = MARGEN, anchoMaximo = util - (x - MARGEN), despues = 1.5 } = {}) {
    doc.setFont('helvetica', negrita ? 'bold' : 'normal');
    doc.setFontSize(tamano);
    doc.setTextColor(...color);
    const altoLinea = tamano * PUNTO_A_MM * 1.35;
    for (const linea of doc.splitTextToSize(contenido, anchoMaximo)) {
      asegurar(altoLinea);
      doc.text(linea, x, y, { baseline: 'top' });
      y += altoLinea;
    }
    y += despues;
  }

  function separador(espacio = 3) {
    asegurar(espacio * 2);
    doc.setDrawColor(...PORCELANA);
    doc.setLineWidth(0.3);
    doc.line(MARGEN, y, ancho - MARGEN, y);
    y += espacio;
  }

  return {
    ancho, alto, util, texto, separador, asegurar,
    get y() { return y; },
    set y(valor) { y = valor; },
  };
}

function encabezado(doc, escritor, vista) {
  doc.setFillColor(...VIOLETA);
  doc.rect(0, 0, escritor.ancho, 24, 'F');
  escritor.y = 7;
  escritor.texto('Simulador Solar Montería', { tamano: 15, negrita: true, color: BLANCO, despues: 0.5 });
  escritor.texto('Resumen de tu simulación', { tamano: 10, color: BLANCO });
  escritor.y = 32;

  const { insignia, titulo, texto, ahorro } = vista.encabezado;
  escritor.texto(insignia.toUpperCase(), { tamano: 9, negrita: true, color: VIOLETA, despues: 1 });
  escritor.texto(titulo, { tamano: 16, negrita: true, despues: 2 });
  escritor.texto(texto, { despues: 3 });
  if (ahorro) {
    escritor.texto(`Ahorro mensual estimado: ${ahorro.valor}`, { tamano: 13, negrita: true, color: VIOLETA, despues: 0.5 });
    escritor.texto(ahorro.detalle, { tamano: 10, despues: 3 });
  }
}

function seccion(escritor, titulo) {
  escritor.asegurar(14);
  escritor.separador(4);
  escritor.texto(titulo, { tamano: 12.5, negrita: true, despues: 2 });
}

function lista(escritor, elementos) {
  for (const elemento of elementos) escritor.texto(`•  ${elemento}`, { tamano: 10, x: MARGEN + 2 });
}

function tarjeta(doc, escritor, { nombre, descripcion, recomendada, indicadores, clasificacion }) {
  escritor.asegurar(70);
  escritor.texto(recomendada ? `${nombre}  ·  Recomendada` : nombre,
    { tamano: 12, negrita: true, color: recomendada ? VIOLETA : TEXTO, despues: 0.5 });
  if (descripcion) escritor.texto(descripcion, { tamano: 9.5, color: TAUPE, despues: 2 });

  const columna = escritor.util / 2;
  for (let indice = 0; indice < indicadores.length; indice += 2) {
    escritor.asegurar(11);
    const inicio = escritor.y;
    let fin = inicio;
    indicadores.slice(indice, indice + 2).forEach(({ etiqueta, valor }, posicion) => {
      escritor.y = inicio;
      const x = MARGEN + posicion * columna;
      escritor.texto(etiqueta, { tamano: 8.5, color: TAUPE, x, anchoMaximo: columna - 4, despues: 0.3 });
      escritor.texto(valor, { tamano: 11, negrita: true, x, anchoMaximo: columna - 4, despues: 2 });
      fin = Math.max(fin, escritor.y);
    });
    escritor.y = fin;
  }
  escritor.texto(`Clasificación: ${clasificacion}`, { tamano: 10, negrita: true, despues: 4 });
}

function gastoMensual(doc, escritor, { filas }) {
  for (const { etiqueta, valor, ancho, tipo } of filas) {
    escritor.asegurar(10);
    const inicio = escritor.y;
    escritor.texto(etiqueta, { tamano: 10, despues: 0 });
    doc.setFont('helvetica', 'bold');
    doc.text(valor, MARGEN + escritor.util, inicio, { baseline: 'top', align: 'right' });
    const y = escritor.y + 0.8;
    doc.setFillColor(...PORCELANA);
    doc.rect(MARGEN, y, escritor.util, 2.2, 'F');
    doc.setFillColor(...({ actual: TAUPE, recomendada: VIOLETA }[tipo] ?? VIOLETA_CLARO));
    doc.rect(MARGEN, y, Math.max(0.8, (escritor.util * ancho) / 100), 2.2, 'F');
    escritor.y = y + 5;
  }
  escritor.texto('Primer año. El ahorro crece con el incremento anual configurado de la tarifa.',
    { tamano: 9, color: TAUPE });
}

function pieDePagina(doc, escritor, generado) {
  const total = doc.getNumberOfPages();
  for (let pagina = 1; pagina <= total; pagina++) {
    doc.setPage(pagina);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8);
    doc.setTextColor(...TAUPE);
    const y = escritor.alto - MARGEN + 4;
    doc.text(`Estimación, no cotización · Generado el ${generado}`, MARGEN, y);
    doc.text(`Página ${pagina} de ${total}`, escritor.ancho - MARGEN, y, { align: 'right' });
  }
}

/** Arma el documento con jsPDF. Recibe la clase para poder probarlo sin navegador. */
export function crearDocumento(resultado, JsPDF, fecha = new Date()) {
  const vista = prepararResultados(resultado);
  const doc = new JsPDF({ unit: 'mm', format: 'letter' });
  const escritor = crearEscritor(doc);
  doc.setProperties({ title: 'Resumen de la simulación solar', subject: 'Simulador Solar Montería' });

  encabezado(doc, escritor, vista);

  seccion(escritor, 'Datos de la estimación');
  lista(escritor, vista.etiquetas.map(({ texto }) => texto));

  if (vista.advertencias.length > 0) {
    seccion(escritor, 'Ten en cuenta');
    lista(escritor, vista.advertencias);
  }

  if (vista.tarjetas.length > 0) {
    seccion(escritor, 'Configuraciones evaluadas');
    vista.tarjetas.forEach((datos) => tarjeta(doc, escritor, datos));
  }

  if (vista.explicacion) {
    seccion(escritor, vista.explicacion.titulo);
    escritor.texto(vista.explicacion.texto, { tamano: 10 });
  }

  if (vista.gastoMensual) {
    seccion(escritor, 'Lo que pagarías de energía cada mes');
    gastoMensual(doc, escritor, vista.gastoMensual);
  }

  if (vista.escenarioSinSombra) {
    const { ahorro, recuperacion, actual } = vista.escenarioSinSombra;
    seccion(escritor, 'Si quitaras la sombra (dato informativo)');
    escritor.texto('Por ejemplo, podando un árbol. Con el mismo techo:', { tamano: 10 });
    lista(escritor, [`Ahorro mensual: ${ahorro}`, `Recuperación: ${recuperacion}`]);
    if (actual) {
      escritor.texto(`Con la sombra actual: ${actual.ahorro} al mes y recuperación en ${actual.recuperacion}.`,
        { tamano: 9.5, color: TAUPE });
    }
  }

  seccion(escritor, 'Aviso');
  escritor.texto('Esta es una estimación y no una cotización. No incluye venta de excedentes, '
    + 'degradación de los paneles ni costo de financiación.', { tamano: 10 });

  pieDePagina(doc, escritor, formatoGeneracion.format(fecha));
  return doc;
}

/** Carga jsPDF solo al pedir el PDF, arma el resumen y lo descarga en el dispositivo. */
export async function descargarResumenPdf(resultado) {
  const { jsPDF } = await import('jspdf');
  const fecha = new Date();
  crearDocumento(resultado, jsPDF, fecha).save(nombreArchivo(fecha));
}
