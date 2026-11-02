import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';

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

/** La operación salía de la cuenta principal y no alcanzó el saldo: se ofrece depositar. */
export function faltaSaldoEnCuenta(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse)) {
    return false;
  }
  const cuerpo = error.error as { codigo?: string; mensaje?: string } | null;
  return cuerpo?.codigo === 'SALDO_INSUFICIENTE' && !!cuerpo.mensaje?.toLowerCase().includes('saldo disponible');
}

/**
 * Si faltó saldo en la cuenta principal, lleva a depositar explicando por qué y devuelve true. Si no, false: el error se
 * muestra donde ocurrió.
 */
export function llevarADepositarSiFaltaSaldo(error: unknown, router: Router, para: string): boolean {
  if (!faltaSaldoEnCuenta(error)) {
    return false;
  }
  router.navigateByUrl('/app/depositos', {
    state: { aviso: `No tiene saldo suficiente en su cuenta principal para ${para}. Deposite dinero y vuelva a intentarlo.` },
  });
  return true;
}
