import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, Pagina, PersonaContacto } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Estado } from '../../shared/components/estado';
import { Paginador } from '../../shared/components/paginador';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-admin-personas',
  imports: [DatePipe, Estado, FormsModule, Paginador],
  template: `
    <h1>Personas</h1>
    <div class="herramientas">
      <label>Buscar por nombre, identificación o correo
        <input type="search" [ngModel]="buscar()" (ngModelChange)="filtrar($event)" />
      </label>
    </div>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (datos(); as d) {
      @if (d.total === 0) {
        <p class="vacio">No se encontraron personas.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th scope="col">Nombre</th><th scope="col">Identificación</th><th scope="col">Contacto</th><th scope="col">Dirección</th><th scope="col">Cuenta</th><th scope="col">Condición</th><th scope="col">Registro</th></tr></thead>
            <tbody>
              @for (p of d.contenido; track p.id) {
                <tr>
                  <td>{{ p.nombres }} {{ p.apellidos }}</td>
                  <td>{{ p.identificacion }}</td>
                  <td>{{ p.correo }}<br /><span class="suave">{{ p.telefono }}</span></td>
                  <td>{{ p.direccion }}</td>
                  <td>@if (p.estadoCuenta) { <app-estado [valor]="p.estadoCuenta" /> } @else { <span class="suave">Sin cuenta</span> }</td>
                  <td>{{ p.socio ? 'Socio' : 'Cliente' }}</td>
                  <td>{{ p.registradaEn | date: 'dd/MM/yyyy' : 'America/Guayaquil' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-paginador [pagina]="d.pagina" [tamano]="d.tamano" [total]="d.total" (cambiar)="cargar($event)" />
      }
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }
  `,
})
export class AdminPersonas implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly buscar = signal('');
  protected readonly datos = signal<Pagina<PersonaContacto> | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.pagina ?? 0));
  }

  ngOnInit(): void {
    this.cargar(0);
  }

  protected filtrar(texto: string): void {
    this.buscar.set(texto);
    this.cargar(0);
  }

  protected cargar(pagina: number): void {
    this.api.personas(this.buscar(), pagina).subscribe({ next: (d) => this.datos.set(d), error: (e) => this.error.set(mensajeDeError(e)) });
  }
}
