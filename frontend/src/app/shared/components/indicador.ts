import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, LucideIconData } from 'lucide-angular';

/** Tarjeta de indicador. El valor llega ya calculado por el backend y formateado por quien la usa. */
@Component({
  selector: 'app-indicador',
  imports: [LucideAngularModule, RouterLink],
  template: `
    <a class="indicador" [routerLink]="ruta()">
      <p class="indicador-titulo">
        <span class="indicador-icono" [class]="'tono-' + tono()"><lucide-icon [img]="icono()" [size]="16" aria-hidden="true" /></span>
        {{ titulo() }}
      </p>
      <p class="indicador-valor">{{ valor() }}</p>
      @if (nota()) { <p class="indicador-nota">{{ nota() }}</p> }
    </a>
  `,
})
export class Indicador {
  readonly titulo = input.required<string>();
  readonly valor = input.required<string | number | null>();
  readonly ruta = input.required<string>();
  readonly icono = input.required<LucideIconData>();
  readonly tono = input<'primario' | 'informacion' | 'advertencia'>('primario');
  readonly nota = input<string | null>(null);
}
