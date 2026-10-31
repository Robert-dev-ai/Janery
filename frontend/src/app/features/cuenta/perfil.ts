import { Component, inject, OnInit, signal } from '@angular/core';

import { AuthService } from '../../core/auth/auth';
import { ApiService, Perfil } from '../../core/services/api';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-perfil',
  template: `
    <h1>Perfil</h1>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    } @else if (perfil(); as p) {
      <dl class="tarjeta">
        <dt><strong>Correo</strong></dt><dd>{{ auth.yo()?.correo }}</dd>
        <dt><strong>Nombre</strong></dt><dd>{{ p.nombres }} {{ p.apellidos }}</dd>
        <dt><strong>Identificación</strong></dt><dd>{{ p.identificacion }}</dd>
        <dt><strong>Teléfono</strong></dt><dd>{{ p.telefono }}</dd>
        <dt><strong>Dirección</strong></dt><dd>{{ p.direccion }}</dd>
      </dl>
    } @else {
      <p role="status">Cargando…</p>
    }
  `,
})
export class PerfilPagina implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);

  protected readonly perfil = signal<Perfil | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.miPerfil().subscribe({
      next: (p) => this.perfil.set(p),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
