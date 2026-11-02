import { Component, effect, ElementRef, inject, Injectable, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

interface Peticion {
  mensaje: string;
  accion: string;
  peligro: boolean;
  pideTexto: boolean;
  responder: (respuesta: string | null) => void;
}

/** Reemplaza a confirm() y prompt() del navegador. Quien llama recibe la respuesta cuando la persona decide. */
@Injectable({ providedIn: 'root' })
export class DialogoService {
  readonly actual = signal<Peticion | null>(null);

  confirmar(mensaje: string, accion = 'Confirmar', peligro = false): Promise<boolean> {
    return this.abrir(mensaje, accion, peligro, false).then((r) => r !== null);
  }

  /** Devuelve el texto escrito, o null si se cancela. */
  pedirTexto(mensaje: string, accion: string): Promise<string | null> {
    return this.abrir(mensaje, accion, true, true);
  }

  private abrir(mensaje: string, accion: string, peligro: boolean, pideTexto: boolean): Promise<string | null> {
    return new Promise((responder) => this.actual.set({ mensaje, accion, peligro, pideTexto, responder }));
  }
}

@Component({
  selector: 'app-dialogo',
  imports: [FormsModule],
  template: `
    <dialog #dialogo class="dialogo" (cancel)="$event.preventDefault(); responder(null)">
      @if (servicio.actual(); as p) {
        <form (ngSubmit)="responder(texto.trim())">
          <p class="dialogo-mensaje">{{ p.mensaje }}</p>
          @if (p.pideTexto) {
            <input name="texto" maxlength="500" aria-label="Motivo" [(ngModel)]="texto" />
          }
          <div class="dialogo-acciones">
            <button type="button" class="secundario" (click)="responder(null)">Cancelar</button>
            <button type="submit" [class.peligro]="p.peligro" [disabled]="p.pideTexto && !texto.trim()">{{ p.accion }}</button>
          </div>
        </form>
      }
    </dialog>
  `,
})
export class Dialogo {
  protected readonly servicio = inject(DialogoService);
  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');
  protected texto = '';

  constructor() {
    effect(() => {
      const elemento = this.dialogo().nativeElement;
      if (this.servicio.actual()) {
        this.texto = '';
        elemento.showModal();
      } else if (elemento.open) {
        elemento.close();
      }
    });
  }

  protected responder(respuesta: string | null): void {
    this.servicio.actual()?.responder(respuesta);
    this.servicio.actual.set(null);
  }
}
