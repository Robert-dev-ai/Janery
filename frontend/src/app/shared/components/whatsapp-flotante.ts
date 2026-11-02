import { Component, computed, inject } from '@angular/core';

import { InfoAppService } from '../../core/services/info-app';

/** Botón fijo para escribir a Janery por WhatsApp. */
@Component({
  selector: 'app-whatsapp-flotante',
  template: `
    @if (enlace(); as url) {
      <a class="whatsapp" [href]="url" target="_blank" rel="noopener noreferrer" aria-label="Escribir a Janery por WhatsApp" title="Escríbanos por WhatsApp">
        <svg viewBox="0 0 32 32" width="30" height="30" aria-hidden="true" fill="currentColor">
          <path d="M16 3C8.8 3 3 8.7 3 15.8c0 2.5.7 4.9 2 7L3 29l6.4-2c2 1.1 4.3 1.7 6.6 1.7 7.2 0 13-5.7 13-12.8S23.2 3 16 3zm0 23.4c-2.1 0-4.1-.6-5.9-1.6l-.4-.3-3.8 1.2 1.2-3.7-.3-.4c-1.2-1.8-1.8-3.8-1.8-5.9C5 9.9 9.9 5.1 16 5.1S27 9.9 27 15.8s-4.9 10.6-11 10.6zm6-7.9c-.3-.2-2-1-2.3-1.1-.3-.1-.5-.2-.7.2-.2.3-.8 1-1 1.2-.2.2-.4.2-.7.1-.3-.2-1.4-.5-2.7-1.6-1-.9-1.7-2-1.9-2.3-.2-.3 0-.5.1-.7l.5-.6c.2-.2.2-.3.3-.6.1-.2 0-.4 0-.6l-1-2.4c-.3-.6-.5-.5-.7-.5h-.6c-.2 0-.6.1-.9.4-.3.3-1.2 1.1-1.2 2.7s1.2 3.2 1.4 3.4c.2.2 2.4 3.6 5.8 5 .8.3 1.4.5 1.9.7.8.3 1.5.2 2.1.1.6-.1 2-.8 2.3-1.6.3-.8.3-1.5.2-1.6-.1-.2-.3-.3-.6-.4z"/>
        </svg>
      </a>
    }
  `,
})
export class WhatsappFlotante {
  private readonly info = inject(InfoAppService);
  protected readonly enlace = computed(() => {
    const numero = this.info.info()?.whatsapp;
    return numero ? `https://wa.me/${numero}?text=${encodeURIComponent('Hola Janery, quisiera información.')}` : null;
  });
}
