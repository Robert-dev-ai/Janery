import { Component, inject, OnInit, signal } from '@angular/core';
import { LucideAngularModule, Megaphone, Pencil } from 'lucide-angular';

import { AuthService } from '../core/auth/auth';
import { ApiService } from '../core/services/api';
import { alCambiar } from '../core/services/tiempo-real';
import { DialogoService } from '../shared/components/dialogo';

/**
 * Aviso de la presidencia que corre por la cabecera, de derecha a izquierda. Se actualiza en vivo al publicarse. Quien
 * puede publicar lo cambia o lo quita desde aquí mismo. Sin aviso, la franja no se muestra.
 */
@Component({
  selector: 'app-anuncio-cabecera',
  imports: [LucideAngularModule],
  host: { class: 'anuncio-contenedor' },
  template: `
    @if (texto(); as t) {
      <div class="anuncio" role="marquee" [attr.aria-label]="'Aviso: ' + t">
        <span class="anuncio-etiqueta"><lucide-icon [img]="iconos.Megaphone" [size]="15" aria-hidden="true" /><span>Aviso</span></span>
        <div class="anuncio-pista">
          <span class="anuncio-texto" [style.--duracion]="duracion(t)">{{ t }}</span>
        </div>
      </div>
    }
    @if (puedePublicar) {
      <button type="button" class="icono-boton" [title]="texto() ? 'Cambiar el aviso' : 'Publicar un aviso'"
        [attr.aria-label]="texto() ? 'Cambiar el aviso' : 'Publicar un aviso'" (click)="editar()">
        <lucide-icon [img]="iconos.Pencil" [size]="16" aria-hidden="true" />
      </button>
    }
  `,
})
export class AnuncioCabecera implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly iconos = { Megaphone, Pencil };
  protected readonly puedePublicar = inject(AuthService).tienePermiso('PUBLICAR_ANUNCIOS');
  protected readonly texto = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    this.api.anuncio().subscribe({ next: (a) => this.texto.set(a.texto), error: () => this.texto.set(null) });
  }

  /** Velocidad pareja: un aviso largo tarda más en cruzar, no corre más rápido. */
  protected duracion(texto: string): string {
    return `${Math.max(12, Math.round(texto.length * 0.22))}s`;
  }

  protected async editar(): Promise<void> {
    if (this.texto() && (await this.dialogo.confirmar('Hay un aviso publicado. ¿Quitarlo? Si prefiere cambiarlo por otro, cancele y escriba el nuevo.', 'Quitar aviso', true))) {
      this.api.publicarAnuncio('').subscribe(() => this.texto.set(null));
      return;
    }
    const nuevo = await this.dialogo.pedirTexto('Aviso para todos (hasta 300 letras). Correrá por la cabecera de cada panel:', 'Publicar aviso');
    if (nuevo?.trim()) {
      this.api.publicarAnuncio(nuevo.trim().slice(0, 300)).subscribe((a) => this.texto.set(a.texto));
    }
  }
}
