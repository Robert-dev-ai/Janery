import { DOCUMENT } from '@angular/common';
import { effect, inject, Injectable, signal } from '@angular/core';

const CLAVE = 'janery-tema';

/** Modo claro u oscuro. Se recuerda en este navegador; por defecto, claro. */
@Injectable({ providedIn: 'root' })
export class TemaService {
  private readonly documento = inject(DOCUMENT);

  readonly oscuro = signal(this.inicial());

  constructor() {
    effect(() => {
      const oscuro = this.oscuro();
      this.documento.documentElement.classList.toggle('oscuro', oscuro);
      this.documento.documentElement.style.colorScheme = oscuro ? 'dark' : 'light';
      try {
        localStorage.setItem(CLAVE, oscuro ? 'oscuro' : 'claro');
      } catch {
        // Sin almacenamiento (modo privado): el tema vale solo para esta visita.
      }
    });
  }

  alternar(): void {
    this.oscuro.update((o) => !o);
  }

  private inicial(): boolean {
    try {
      const guardado = localStorage.getItem(CLAVE);
      if (guardado) {
        return guardado === 'oscuro';
      }
    } catch {
      // Igual que arriba.
    }
    return false;
  }
}
