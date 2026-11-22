export function MensajeApi({ error }) {
  return (
    <>
      <div>{error.message}</div>
      {error.detalles?.length > 0 && (
        <ul>{error.detalles.map((detalle, indice) => <li key={indice}>{detalle}</li>)}</ul>
      )}
    </>
  );
}
