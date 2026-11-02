import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { TemaService } from './core/services/tema';
import { UsoService } from './core/services/uso';
import { Dialogo } from './shared/components/dialogo';

@Component({
  selector: 'app-root',
  imports: [Dialogo, RouterOutlet],
  template: '<router-outlet /><app-dialogo />',
})
export class App {
  // Se crean al arrancar: el tema guardado se aplica antes de mostrar cualquier pantalla, y cada visita se anota.
  protected readonly tema = inject(TemaService);
  protected readonly uso = inject(UsoService);
}
