import { CurrencyPipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { AuthService } from '../../core/auth/auth';
import { ApiService, Credito, DocumentoCredito, Pagina, Solicitante, SolicitudCupo, VotoCredito } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { AREAS_APROBACION, nombreArea, nombreFamilia } from '../../shared/utils/etiquetas';
import { Estado } from '../../shared/components/estado';
import { Paginador } from '../../shared/components/paginador';
import { DialogoService } from '../../shared/components/dialogo';
import { descargar } from '../../shared/utils/descarga';
import { mensajeDeError } from '../../shared/utils/errores';
import { CreditoExistenteForm } from './credito-existente';
import { TramiteCredito } from './tramite-credito';

@Component({
  selector: 'app-admin-creditos',
  imports: [CreditoExistenteForm, CurrencyPipe, DecimalPipe, Estado, FormsModule, NgTemplateOutlet, Paginador, TramiteCredito],
  template: `
    <div class="encabezado-pagina">
      <h1>Créditos</h1>
      <div class="acciones">
        @if (puedeRegistrarExistentes) {
          <button type="button" class="secundario" (click)="registrandoExistente.set(!registrandoExistente())">{{ registrandoExistente() ? 'Cerrar registro' : '+ Registrar crédito que ya existía' }}</button>
        }
        <button type="button" (click)="tramitando.set(!tramitando())">{{ tramitando() ? 'Cerrar trámite' : '+ Tramitar un crédito en ventanilla' }}</button>
      </div>
    </div>
    @if (registrandoExistente()) { <app-credito-existente (registrado)="filtrar('ACTIVO')" /> }
    @if (tramitando()) { <app-tramite-credito (registrado)="cargar(0)" /> }
    <p class="suave">Cada crédito se decide con tres votos: presidencia o vicepresidencia, tesorería y comité de crédito. Se aprueba y desembolsa solo si los tres aprueban; si alguno rechaza, queda rechazado al completarse los votos. Cada persona vota una vez.</p>
    <div class="herramientas">
      <label>Estado
        <select [ngModel]="estado()" (ngModelChange)="filtrar($event)">
          <option value="SOLICITADO">Por aprobar</option>
          <option value="ACTIVO">Activos</option>
          <option value="FINALIZADO">Finalizados</option>
          <option value="RECHAZADO">Rechazados</option>
        </select>
      </label>
      <label>Buscar en esta página
        <input type="search" placeholder="Titular o tipo" [ngModel]="busqueda()" (ngModelChange)="busqueda.set($event)" />
      </label>
    </div>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (cupos().length) {
      <section class="seccion">
        <h2 class="seccion-titulo">Retiros de cupo emergente por aprobar</h2>
        <div class="tabla">
          <table>
            <thead><tr><th>Código</th><th>Socio</th><th>Destino</th><th class="num">Importe</th><th>Votos</th><th>Su voto</th></tr></thead>
            <tbody>
              @for (s of cupos(); track s.id) {
                <tr>
                  <td class="codigo">{{ s.codigo }}</td>
                  <td>{{ s.titular }}</td>
                  <td>{{ s.promocion ?? 'Cupo general' }}</td>
                  <td class="num">{{ s.importe | currency: 'USD' }}</td>
                  <td><ng-container *ngTemplateOutlet="votos; context: { $implicit: s.aprobaciones }" /></td>
                  <td class="acciones">
                    <button type="button" (click)="aprobarCupo(s)">Aprobar</button>
                    <button type="button" class="peligro" (click)="rechazarCupo(s)">Rechazar</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }
    @if (solicitante(); as s) {
      <section class="tarjeta">
        <div class="encabezado-tarjeta"><h2>{{ s.nombre }}</h2><button type="button" class="secundario" (click)="solicitante.set(null)">Cerrar</button></div>
        <dl class="ficha-datos">
          <div><dt>Identificación</dt><dd>{{ s.identificacion }}</dd></div>
          <div><dt>Teléfono</dt><dd>{{ s.telefono }}</dd></div>
          <div><dt>Dirección</dt><dd>{{ s.direccion ?? '—' }}</dd></div>
          <div><dt>Fecha de nacimiento</dt><dd>{{ s.fechaNacimiento ?? '—' }}</dd></div>
          <div><dt>Estado civil</dt><dd>{{ s.estadoCivil ?? '—' }}</dd></div>
          <div><dt>Lugar de trabajo</dt><dd>{{ s.lugarTrabajo ?? '—' }}</dd></div>
          <div><dt>Ocupación y antigüedad</dt><dd>{{ s.ocupacion ?? '—' }} · {{ s.antiguedadTrabajo ?? '—' }}</dd></div>
          <div><dt>Ingresos mensuales</dt><dd>{{ s.ingresosMensuales === null ? '—' : (s.ingresosMensuales | currency: 'USD') }}</dd></div>
          <div><dt>Referencia</dt><dd>{{ s.referenciaNombre ?? '—' }} · {{ s.referenciaTelefono ?? '—' }}</dd></div>
          <div><dt>Cuenta bancaria</dt><dd>{{ s.banco ?? '—' }} · {{ s.tipoCuentaBanco ?? '' }} {{ s.numeroCuentaBanco ?? '' }}</dd></div>
        </dl>
        <h3>Documentos</h3>
        @for (d of documentos(); track d.id) {
          <button type="button" class="secundario mr-2 mb-2" (click)="descargarDocumento(d)">{{ d.nombre }}</button>
        } @empty {
          <p class="suave">No subió documentos.</p>
        }
      </section>
    }

    @if (datos(); as d) {
      @if (d.total === 0) {
        <p class="vacio">No hay créditos en este estado.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Código</th><th>Titular</th><th>Tipo</th><th>Perfil</th><th>Estado</th><th class="num">Importe</th><th>Plazo</th><th>Tasa</th><th class="num">Pendiente</th><th>Acciones</th></tr></thead>
            <tbody>
              @for (c of filas(); track c.id) {
                <tr>
                  <td class="codigo">{{ c.codigo }}</td>
                  <td>{{ c.titular }}</td>
                  <td>{{ familia(c.familia) }} {{ c.modalidad ?? '' }}
                    @if (c.existente) { <br /><small class="suave">Anterior a Janery</small> }
                    @if (c.origenEncaje && c.estado === 'SOLICITADO') { <br /><small class="suave">Encaje: {{ origen(c.origenEncaje) }}</small> }
                    @if (c.letrasAtrasadas > 0) { <br /><small class="negativo">{{ c.letrasAtrasadas }} atrasada{{ c.letrasAtrasadas === 1 ? '' : 's' }}@if (c.moraPendiente > 0) { · mora {{ c.moraPendiente | currency: 'USD' }} }</small> }
                  </td>
                  <td>{{ c.perfil }}</td>
                  <td><app-estado [valor]="c.estado" />
                    @if (c.estado === 'SOLICITADO') {
                      <ng-container *ngTemplateOutlet="votos; context: { $implicit: c.aprobaciones }" />
                      @if (c.perfil === 'CLIENTE' && (c.familia === 'LETRAS' || c.familia === 'EMERGENTE_CLIENTE') && !c.documentos) { <small class="negativo">Sin solicitud firmada</small> }
                    }
                  </td>
                  <td class="num">{{ c.importe | currency: 'USD' }}</td>
                  <td>{{ c.plazoMeses ? c.plazoMeses + ' meses' : '—' }}</td>
                  <td>{{ c.tasaInteres | number: '1.0-4' }} {{ c.unidadTasa === 'PCT_DIARIO' ? '% diario' : '% mensual' }}</td>
                  <td class="num">{{ c.totalPendiente | currency: 'USD' }}</td>
                  <td class="acciones">
                    @if (c.estado === 'SOLICITADO') {
                      <button type="button" class="secundario" (click)="verSolicitante(c)">Ver datos</button>
                      <button type="button" (click)="aprobar(c)">Aprobar</button>
                      <button type="button" class="peligro" (click)="rechazar(c)">Rechazar</button>
                    } @else if (c.motivoRechazo) {
                      {{ c.motivoRechazo }}
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-paginador [pagina]="d.pagina" [tamano]="d.tamano" [total]="d.total" (cambiar)="cargar($event)" />
      }
    }

    <ng-template #votos let-lista>
      <ul class="votos mt-1" aria-label="Votos">
        @for (a of areas; track a) {
          @if (votoDe(lista, a); as v) {
            <li [class.aprueba]="v.decision === 'APROBAR'" [class.rechaza]="v.decision === 'RECHAZAR'" [title]="v.motivo ?? ''">{{ area(a) }}: {{ v.nombre }} {{ v.decision === 'APROBAR' ? 'aprobó' : 'rechazó' }}</li>
          } @else {
            <li>{{ area(a) }}: falta</li>
          }
        }
      </ul>
    </ng-template>
  `,
})
export class AdminCreditos implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly familia = nombreFamilia;
  protected readonly tramitando = signal(false);
  protected readonly registrandoExistente = signal(false);
  protected readonly puedeRegistrarExistentes = inject(AuthService).tienePermiso('REGISTRAR_CREDITOS_EXISTENTES');
  protected readonly areas = AREAS_APROBACION;
  protected readonly area = nombreArea;

  protected votoDe(lista: VotoCredito[], area: string): VotoCredito | undefined {
    return lista?.find((v) => v.area === area);
  }
  protected readonly estado = signal('SOLICITADO');
  protected readonly datos = signal<Pagina<Credito> | null>(null);
  protected readonly busqueda = signal('');
  protected readonly filas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return (this.datos()?.contenido ?? []).filter((x) => !texto || (x.titular + ' ' + x.familia + ' ' + (x.modalidad ?? '')).toLowerCase().includes(texto));
  });
  protected readonly error = signal<string | null>(null);
  protected readonly solicitante = signal<Solicitante | null>(null);
  protected readonly cupos = signal<SolicitudCupo[]>([]);
  protected readonly documentos = signal<DocumentoCredito[]>([]);
  private creditoVisto: number | null = null;

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.pagina ?? 0));
  }

  ngOnInit(): void {
    this.cargar(0);
  }

  protected filtrar(estado: string): void {
    this.estado.set(estado);
    this.cargar(0);
  }

  protected cargar(pagina: number): void {
    this.api.solicitudesCupoAdmin().subscribe({ next: (s) => this.cupos.set(s), error: (e) => this.error.set(mensajeDeError(e)) });
    this.api.creditosAdmin(this.estado(), pagina).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected verSolicitante(c: Credito): void {
    this.creditoVisto = c.id;
    this.api.solicitante(c.id).subscribe({ next: (s) => this.solicitante.set(s), error: (e) => this.error.set(mensajeDeError(e)) });
    this.api.documentosCreditoAdmin(c.id).subscribe({ next: (d) => this.documentos.set(d), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected descargarDocumento(d: DocumentoCredito): void {
    this.api.descargarDocumentoCredito(this.creditoVisto!, d.id).subscribe({
      next: (archivo) => descargar(archivo, d.nombre),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected origen(codigo: string): string {
    return ({ EFECTIVO: 'en efectivo', CUENTA: 'de la cuenta principal', AHORROS: 'de sus ahorros', CREDITO: 'descontado del crédito' } as Record<string, string>)[codigo] ?? codigo;
  }

  protected async aprobarCupo(s: SolicitudCupo): Promise<void> {
    if (await this.dialogo.confirmar(`¿Dar su voto de aprobación al retiro de ${s.importe} USD de ${s.titular}? Se desembolsa cuando las tres áreas aprueban.`, 'Aprobar')) {
      this.ejecutar(this.api.aprobarCupo(s.id));
    }
  }

  protected async rechazarCupo(s: SolicitudCupo): Promise<void> {
    const motivo = await this.dialogo.pedirTexto(`Motivo del rechazo a ${s.titular}:`, 'Rechazar');
    if (motivo) {
      this.ejecutar(this.api.rechazarCupo(s.id, motivo));
    }
  }

  protected async aprobar(c: Credito): Promise<void> {
    const suministros = c.suministros ? ` y ${c.suministros} USD de suministros` : '';
    const efectivo = c.origenEncaje === 'EFECTIVO' ? ` Confirme que recibió en efectivo el encaje${suministros}.` : '';
    if (await this.dialogo.confirmar(`¿Dar su voto de aprobación al crédito de ${c.importe} USD de ${c.titular}? Se desembolsa cuando las tres áreas aprueban.${efectivo}`, 'Aprobar')) {
      this.ejecutar(this.api.aprobarCredito(c.id));
    }
  }

  protected async rechazar(c: Credito): Promise<void> {
    const motivo = await this.dialogo.pedirTexto(`Motivo del rechazo a ${c.titular}:`, 'Rechazar');
    if (motivo) {
      this.ejecutar(this.api.rechazarCredito(c.id, motivo));
    }
  }

  private ejecutar(accion: Observable<void>): void {
    this.error.set(null);
    accion.subscribe({
      next: () => this.cargar(this.datos()?.pagina ?? 0),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
