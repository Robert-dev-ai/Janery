/** Guarda un archivo recibido de la API. Se pide con HttpClient porque la API exige el token. */
export function descargar(archivo: Blob, nombre: string): void {
  const url = URL.createObjectURL(archivo);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  enlace.click();
  URL.revokeObjectURL(url);
}
