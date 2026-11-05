import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withPreloading } from '@angular/router';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth';
import { authInterceptor } from './core/auth/auth.interceptor';
import { PrecargaConSesion } from './core/services/precarga';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    // Tras abrir la primera pantalla, descarga en segundo plano las que puede usar: ver PrecargaConSesion.
    provideRouter(routes, withPreloading(PrecargaConSesion)),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAppInitializer(() => inject(AuthService).restaurarSesion())
  ]
};
