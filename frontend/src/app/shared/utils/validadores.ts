import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Cédula ecuatoriana: diez números. */
export const cedulaValida: ValidatorFn = (c: AbstractControl): ValidationErrors | null =>
  !c.value || /^\d{10}$/.test(c.value) ? null : { cedula: true };

export const PREFIJO_ECUADOR = '+593';

/**
 * Teléfono guardado como "+593 987654321": prefijo del país y número sin el 0 inicial. En Ecuador, un celular tiene
 * 9 números sin el 0 y un fijo, 8.
 */
export function telefonoEsValido(valor: string | null | undefined): boolean {
  const partes = /^(\+\d{1,4}) (\d+)$/.exec(valor ?? '');
  if (!partes) {
    return false;
  }
  const [, prefijo, numero] = partes;
  if (prefijo === PREFIJO_ECUADOR) {
    return numero.startsWith('9') ? numero.length === 9 : numero.length === 8;
  }
  return numero.length >= 6 && numero.length <= 12;
}

export const telefonoValido: ValidatorFn = (c: AbstractControl): ValidationErrors | null =>
  !c.value || telefonoEsValido(c.value) ? null : { telefono: true };

/** Separa un teléfono guardado, aunque venga en formatos anteriores como "0991234567". */
export function separarTelefono(valor: string | null | undefined): { prefijo: string; numero: string } {
  const texto = (valor ?? '').trim();
  const partes = /^(\+\d{1,4})\s*(\d*)$/.exec(texto);
  if (partes && texto.includes(' ')) {
    return { prefijo: partes[1], numero: partes[2] };
  }
  if (texto.startsWith(PREFIJO_ECUADOR)) {
    return { prefijo: PREFIJO_ECUADOR, numero: texto.slice(PREFIJO_ECUADOR.length).replace(/\D/g, '') };
  }
  return { prefijo: PREFIJO_ECUADOR, numero: texto.replace(/\D/g, '').replace(/^0/, '') };
}

/** Fecha más reciente para alguien de 18 años, en formato del campo de fecha. */
export function fechaMayorDeEdad(): string {
  const hoy = new Date();
  const limite = new Date(hoy.getFullYear() - 18, hoy.getMonth(), hoy.getDate());
  return limite.toISOString().slice(0, 10);
}

export const mayorDeEdad: ValidatorFn = (c: AbstractControl): ValidationErrors | null =>
  !c.value || c.value <= fechaMayorDeEdad() ? null : { menorDeEdad: true };

const MENSAJES: Record<string, string> = {
  required: 'es obligatorio',
  pattern: 'tiene un formato no válido',
  email: 'no es un correo válido',
  minlength: 'es muy corto',
  maxlength: 'es muy largo',
  min: 'no puede ser negativo',
  cedula: 'debe tener 10 números',
  telefono: 'no es un número válido',
  menorDeEdad: 'indica que es menor de edad',
};

/**
 * Qué datos están mal, para mostrarlo junto al botón de guardar. Solo cuenta lo que la persona ya tocó, más todo si
 * intentó enviar.
 */
export function erroresDeFormulario(form: FormGroup, etiquetas: Record<string, string>, todos = false): string[] {
  return Object.entries(form.controls)
    .filter(([, c]) => c.invalid && (todos || c.dirty || c.touched))
    .map(([nombre, c]) => {
      const clave = Object.keys(c.errors ?? {})[0];
      return `${etiquetas[nombre] ?? nombre} ${MENSAJES[clave] ?? 'no es válido'}`;
    });
}
