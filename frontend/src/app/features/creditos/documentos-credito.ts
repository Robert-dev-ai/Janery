import { DatePipe } from '@angular/common';
import { Component, inject, input, OnInit, signal } from '@angular/core';

import { ApiService, DocumentoCredito } from '../../core/services/api';
import { mensajeDeError } from '../../shared/utils/errores';

/** Panel de documentos de una solicitud: admite varios PDF. Por ahora solo se guardan para revisarlos al aprobar. */
@Component({
  selector: 'app-documentos-credito',
  imports: [DatePipe],
  template: `
    <div class="tarjeta mt-4">
      <h3>Documentos de la solicitud</h3>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @for (d of documentos(); track d.id) {
        <p class="suave">{{ d.nombre }} · {{ kb(d.tamano) }} KB · {{ d.creadoEn | date: 'mediumDate' : 'America/Guayaquil' }}</p>
      } @empty {
        <p class="suave">Aún no ha subido documentos.</p>
      }
      <label>Agregar documentos en PDF (hasta 10 MB cada uno)
        <input type="file" accept="application/pdf,.pdf" multiple [disabled]="subiendo()" (change)="subir($event)" />
      </label>
      @if (subiendo()) { <p class="cargando" role="status">Subiendo…</p> }
    </div>
  `,
})
export class DocumentosCredito implements OnInit {
  readonly creditoId = input.required<number>();

  private readonly api = inject(ApiService);

  protected readonly documentos = signal<DocumentoCredito[]>([]);
  protected readonly subiendo = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.misDocumentosCredito(this.creditoId()).subscribe({
      next: (d) => this.documentos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected kb(bytes: number): number {
    return Math.max(1, Math.round(bytes / 1024));
  }

  protected subir(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const archivos = Array.from(campo.files ?? []);
    if (!archivos.length) {
      return;
    }
    this.subiendo.set(true);
    this.error.set(null);
    this.api.subirDocumentosCredito(this.creditoId(), archivos).subscribe({
      next: (d) => {
        this.documentos.set(d);
        this.subiendo.set(false);
        campo.value = '';
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.subiendo.set(false);
        campo.value = '';
      },
    });
  }
}
