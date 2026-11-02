import { effect, inject, Injectable, signal, untracked } from '@angular/core';

import { AuthService } from '../auth/auth';

/** Foto de perfil como URL local: la imagen se pide con el token, así que no puede ir directo en un <img src>. */
@Injectable({ providedIn: 'root' })
export class FotoPerfilService {
  private readonly auth = inject(AuthService);

  readonly url = signal<string | null>(null);
  private version = '';

  constructor() {
    effect(() => {
      const yo = this.auth.yo();
      // Se vuelve a pedir solo si cambia la persona o si sube otra foto: los avisos en vivo recargan "yo" a menudo y no
      // deben descargar la imagen cada vez.
      const clave = yo?.tieneFoto ? `${yo.id}-${this.auth.versionFoto()}` : '';
      untracked(() => this.cargar(clave));
    });
  }

  private cargar(clave: string): void {
    if (clave === this.version) {
      return;
    }
    this.version = clave;
    this.liberar();
    if (!clave) {
      return;
    }
    this.auth.foto().subscribe({
      next: (blob) => {
        if (this.version === clave) {
          this.url.set(URL.createObjectURL(blob));
        }
      },
      error: () => this.url.set(null),
    });
  }

  private liberar(): void {
    const actual = this.url();
    if (actual) {
      URL.revokeObjectURL(actual);
    }
    this.url.set(null);
  }
}
