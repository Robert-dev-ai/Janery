import { Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

import { PREFIJO_ECUADOR, separarTelefono } from '../utils/validadores';

/**
 * Teléfono con el prefijo del país aparte, +593 por defecto. Solo admite números. En Ecuador se puede escribir el 0
 * inicial, pero no se guarda: con 0 caben 10 números; sin él, 9.
 */
@Component({
  selector: 'app-campo-telefono',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => CampoTelefono), multi: true }],
  template: `
    <span class="campo-telefono">
      <input class="campo-telefono-prefijo" inputmode="tel" maxlength="5" aria-label="Prefijo del país"
        [value]="prefijo()" [disabled]="desactivado()" (input)="cambiarPrefijo($event)" (blur)="tocado()" />
      <input [id]="idCampo()" inputmode="numeric" autocomplete="tel-national" [attr.maxlength]="maximo()"
        [attr.aria-label]="etiqueta()" placeholder="987654321"
        [value]="numero()" [disabled]="desactivado()" (input)="cambiarNumero($event)" (blur)="tocado()" />
    </span>
  `,
})
export class CampoTelefono implements ControlValueAccessor {
  readonly idCampo = input<string | null>(null);
  readonly etiqueta = input('Número de teléfono');

  protected readonly prefijo = signal(PREFIJO_ECUADOR);
  protected readonly numero = signal('');
  protected readonly desactivado = signal(false);

  private alCambiar: (valor: string) => void = () => {};
  private alTocar: () => void = () => {};

  protected maximo(): number {
    if (this.prefijo() !== PREFIJO_ECUADOR) {
      return 12;
    }
    return this.numero().startsWith('0') ? 10 : 9;
  }

  writeValue(valor: string | null): void {
    const partes = separarTelefono(valor);
    this.prefijo.set(partes.prefijo);
    this.numero.set(partes.numero);
  }

  registerOnChange(fn: (valor: string) => void): void {
    this.alCambiar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.alTocar = fn;
  }

  setDisabledState(desactivado: boolean): void {
    this.desactivado.set(desactivado);
  }

  protected cambiarPrefijo(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const limpio = '+' + campo.value.replace(/\D/g, '').slice(0, 4);
    campo.value = limpio;
    this.prefijo.set(limpio);
    this.emitir();
  }

  protected cambiarNumero(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const limpio = campo.value.replace(/\D/g, '').slice(0, this.prefijo() === PREFIJO_ECUADOR && campo.value.startsWith('0') ? 10 : this.maximo());
    campo.value = limpio;
    this.numero.set(limpio);
    this.emitir();
  }

  protected tocado(): void {
    this.alTocar();
  }

  private emitir(): void {
    const numero = this.prefijo() === PREFIJO_ECUADOR ? this.numero().replace(/^0/, '') : this.numero();
    this.alCambiar(numero ? `${this.prefijo()} ${numero}` : '');
  }
}
