import { inject, Injectable, signal } from '@angular/core';

import { ApiService, InfoApp } from './api';

/** Versión, contacto y simulador: se piden una vez al abrir la aplicación. */
@Injectable({ providedIn: 'root' })
export class InfoAppService {
  readonly info = signal<InfoApp | null>(null);

  constructor() {
    inject(ApiService).infoApp().subscribe({ next: (i) => this.info.set(i), error: () => this.info.set(null) });
  }
}
