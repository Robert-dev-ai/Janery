import { HttpErrorResponse } from '@angular/common/http';

/** Mensaje seguro del backend para mostrar al usuario. */
export function mensajeDeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No se pudo conectar con el servidor.';
    }
    const cuerpo = error.error as { mensaje?: string; detalles?: string[] } | null;
    if (cuerpo?.mensaje) {
      return cuerpo.detalles?.length ? `${cuerpo.mensaje}: ${cuerpo.detalles.join('; ')}` : cuerpo.mensaje;
    }
  }
  return 'Ocurrió un error inesperado.';
}
