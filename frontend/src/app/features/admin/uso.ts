import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, UsoApp } from '../../core/services/api';
import { GraficoBarras } from '../../shared/components/grafico-barras';
import { mensajeDeError } from '../../shared/utils/errores';
import { tituloDeRuta } from '../../layout/navegacion';

/** Qué pantallas usa más la gente: para decidir qué áreas mejorar primero. */
@Component({
  selector: 'app-uso',
  imports: [DatePipe, FormsModule, GraficoBarras],
  template: `
    <h1>Uso de la aplicación</h1>
    <p class="suave">Qué pantallas abren más las personas. Las más visitadas son las primeras candidatas para mejorar. Solo se anota la pantalla, nunca lo que se escribe en ella.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    <form class="herramientas" (ngSubmit)="cargar()">
      <label>Desde <input type="date" name="desde" [(ngModel)]="desde" /></label>
      <label>Hasta <input type="date" name="hasta" [(ngModel)]="hasta" /></label>
      <button type="submit">Ver</button>
    </form>

    @if (datos(); as d) {
      <div class="rejilla-indicadores">
        <div class="indicador"><p class="indicador-titulo">Visitas</p><p class="indicador-valor">{{ d.visitas }}</p>
          <p class="indicador-nota">Del {{ d.desde | date: 'dd/MM/yyyy' }} al {{ d.hasta | date: 'dd/MM/yyyy' }}</p></div>
        <div class="indicador"><p class="indicador-titulo">Personas distintas</p><p class="indicador-valor">{{ d.personas }}</p></div>
        @for (g of d.grupos; track g.grupo) {
          <div class="indicador"><p class="indicador-titulo">{{ g.grupo }}</p><p class="indicador-valor">{{ g.visitas }}</p>
            <p class="indicador-nota">{{ g.personas }} {{ g.personas === 1 ? 'persona' : 'personas' }}</p></div>
        }
      </div>

      @if (d.visitas === 0) {
        <p class="vacio">Aún no hay visitas en estas fechas.</p>
      } @else {
        <div class="rejilla-dos">
          <section class="tarjeta">
            <h2>Pantallas más visitadas</h2>
            <app-grafico-barras [datos]="barras()" />
          </section>
          <section class="tarjeta">
            <h2>Detalle</h2>
            <div class="tabla">
              <table>
                <thead><tr><th scope="col">Pantalla</th><th scope="col" class="num">Visitas</th><th scope="col" class="num">Personas</th></tr></thead>
                <tbody>
                  @for (p of d.pantallas; track p.ruta) {
                    <tr><td>{{ titulo(p.ruta) }}<br /><small class="codigo">{{ p.ruta }}</small></td><td class="num">{{ p.visitas }}</td><td class="num">{{ p.personas }}</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        </div>
        <section class="tarjeta">
          <h2>Visitas por día</h2>
          <div class="tabla">
            <table>
              <thead><tr><th scope="col">Día</th><th scope="col" class="num">Visitas</th><th scope="col" class="num">Personas</th></tr></thead>
              <tbody>
                @for (x of d.dias; track x.dia) {
                  <tr><td>{{ x.dia | date: 'EEE dd/MM/yyyy' }}</td><td class="num">{{ x.visitas }}</td><td class="num">{{ x.personas }}</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }
  `,
})
export class UsoAplicacion implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly titulo = tituloDeRuta;
  protected readonly datos = signal<UsoApp | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly barras = computed(() => (this.datos()?.pantallas ?? []).slice(0, 12)
    .map((p) => ({ etiqueta: tituloDeRuta(p.ruta), valor: p.visitas, texto: String(p.visitas) })));
  protected desde = '';
  protected hasta = '';

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.error.set(null);
    this.api.usoApp(this.desde || null, this.hasta || null).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
