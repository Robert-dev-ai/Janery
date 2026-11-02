import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-paginador',
  template: `
    <div class="paginador">
      <button type="button" class="secundario" [disabled]="pagina() === 0" (click)="cambiar.emit(pagina() - 1)">Anterior</button>
      <span>Página {{ pagina() + 1 }} · {{ total() }} en total</span>
      <button type="button" class="secundario" [disabled]="(pagina() + 1) * tamano() >= total()" (click)="cambiar.emit(pagina() + 1)">Siguiente</button>
    </div>
  `,
})
export class Paginador {
  readonly pagina = input.required<number>();
  readonly tamano = input.required<number>();
  readonly total = input.required<number>();
  readonly cambiar = output<number>();
}
