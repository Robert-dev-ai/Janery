import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { of, switchMap } from 'rxjs';

import { ApiService, Perfil, PersonaTramite, ProductosCredito, Simulacion } from '../../core/services/api';
import { mensajeDeError } from '../../shared/utils/errores';
import { FormDatosCredito } from '../configuracion/form-datos-credito';

/**
 * Tesorería tramita el crédito de quien llega a la ventanilla: busca a la persona, revisa sus datos, arma la solicitud y
 * sube la solicitud firmada. Después se aprueba con los tres votos, como cualquier otra.
 */
@Component({
  selector: 'app-tramite-credito',
  imports: [CurrencyPipe, DecimalPipe, FormDatosCredito, FormsModule],
  template: `
    <section class="seccion seccion-accion">
      <h2 class="seccion-titulo">Tramitar un crédito en ventanilla</h2>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

      @if (!persona()) {
        <form class="herramientas" (ngSubmit)="buscar()">
          <label>Persona <input type="search" name="buscar" placeholder="Nombre, cédula o número de cuenta" [(ngModel)]="texto" /></label>
          <button type="submit">Buscar</button>
        </form>
        @for (p of resultados(); track p.usuarioId) {
          <div class="fila-dato tarjeta">
            <div><strong>{{ p.nombre }}</strong><p class="suave">{{ p.identificacion }} · {{ p.cuenta }} · {{ p.socio ? 'Socio' : 'Cliente' }}</p></div>
            <button type="button" (click)="elegir(p)">Tramitar</button>
          </div>
        } @empty {
          @if (buscado()) { <p class="vacio">Nadie con cuenta activa coincide con la búsqueda.</p> }
        }
      } @else {
        <div class="encabezado-tarjeta">
          <p class="m-0"><strong>{{ persona()!.nombre }}</strong> · {{ persona()!.identificacion }} · {{ persona()!.socio ? 'Socio' : 'Cliente' }}</p>
          <button type="button" class="secundario" (click)="reiniciar()">Elegir a otra persona</button>
        </div>

        <label>Tipo de crédito
          <select name="tipo" [(ngModel)]="tipo">
            <option value="LETRAS">Crédito a letras</option>
            <option value="EMERGENTE">{{ persona()!.socio ? 'Retiro de su cupo emergente' : 'Crédito emergente' }}</option>
          </select>
        </label>

        @if (tipo === 'EMERGENTE') {
          <form class="tarjeta" (ngSubmit)="emergente()">
            @if (productos(); as p) {
              <p class="suave">{{ p.perfil === 'SOCIO' ? 'Cupo disponible: ' : 'Límite: ' }}
                {{ (p.perfil === 'SOCIO' ? p.cupoDisponible : p.emergenteLimite) | currency: 'USD' }}</p>
            }
            <label>Importe (USD) <input type="number" min="0" step="1" name="importeE" [(ngModel)]="importe" /></label>
            @if (!persona()!.socio) {
              <label>Solicitud firmada en PDF (obligatoria)
                <input type="file" accept="application/pdf,.pdf" multiple (change)="elegirPdf($event)" />
              </label>
            }
            <button type="submit" [disabled]="!importe || enviando() || (!persona()!.socio && !pdfs.length)">Registrar solicitud</button>
          </form>
        } @else {
          @if (!datosListos()) {
            <p><strong>Paso 1 de 2.</strong> Revise con la persona sus datos para el crédito y corrija lo que haya cambiado.</p>
            @if (perfil(); as pf) {
              <app-form-datos-credito [perfil]="pf" [usuarioId]="persona()!.usuarioId" textoBoton="Datos confirmados, continuar" (guardado)="confirmarDatos($event)" />
            }
          } @else {
            <p><strong>Paso 2 de 2.</strong> El crédito. <button type="button" class="enlace-boton" (click)="datosListos.set(false)">Volver a los datos</button></p>
            <form class="tarjeta" (ngSubmit)="simular()">
              @if (productos(); as p) {
                <label>Modalidad
                  <select name="modalidad" [(ngModel)]="modalidad">
                    @for (m of p.letras; track m.modalidad) {
                      <option [value]="m.modalidad">{{ m.modalidad }}: {{ m.tasaInteres | number: '1.0-4' }} % mensual, encaje {{ m.porcentajeEncaje | number: '1.0-4' }} %</option>
                    }
                  </select>
                </label>
                <label>Importe (USD, de {{ p.importeMinimo | number }} a {{ p.importeMaximo | number }})
                  <input type="number" min="0" step="1" name="importeL" [(ngModel)]="importe" /></label>
                <label>Plazo (de {{ p.plazoMinimo }} a {{ p.plazoMaximo }} meses) <input type="number" step="1" name="plazo" [(ngModel)]="plazo" /></label>
              }
              <button type="submit" class="secundario" [disabled]="!importe || !plazo">Simular</button>
            </form>
            @if (simulacion(); as s) {
              <p>Letra mensual: <strong>{{ s.cuotas[0]?.total | currency: 'USD' }}</strong> · Encaje: <strong>{{ s.encaje | currency: 'USD' }}</strong></p>
              <label>¿De dónde paga el encaje?
                <select name="origen" [(ngModel)]="origen">
                  <option value="">Elija una opción</option>
                  <option value="EFECTIVO">En efectivo, al aprobarse</option>
                  <option value="CUENTA">De su cuenta principal</option>
                  <option value="CREDITO">Descontado del crédito</option>
                </select>
              </label>
              <label>{{ persona()!.socio ? 'Documentos en PDF (opcional)' : 'Solicitud firmada en PDF (obligatoria)' }}
                <input type="file" accept="application/pdf,.pdf" multiple (change)="elegirPdf($event)" />
              </label>
              <button type="button" [disabled]="!origen || enviando() || (!persona()!.socio && !pdfs.length)" (click)="letras()">Registrar solicitud</button>
            }
          }
        }
      }
    </section>
  `,
})
export class TramiteCredito {
  readonly registrado = output<void>();

  private readonly api = inject(ApiService);

  protected readonly resultados = signal<PersonaTramite[]>([]);
  protected readonly buscado = signal(false);
  protected readonly persona = signal<PersonaTramite | null>(null);
  protected readonly perfil = signal<Perfil | null>(null);
  protected readonly productos = signal<ProductosCredito | null>(null);
  protected readonly simulacion = signal<Simulacion | null>(null);
  protected readonly datosListos = signal(false);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected texto = '';
  protected tipo = 'LETRAS';
  protected modalidad = 'NORMAL';
  protected importe: number | null = null;
  protected plazo: number | null = null;
  protected origen = '';
  protected pdfs: File[] = [];
  private clave: string = crypto.randomUUID();

  protected buscar(): void {
    this.error.set(null);
    this.api.buscarPersonasTramite(this.texto.trim()).subscribe({
      next: (l) => {
        this.resultados.set(l);
        this.buscado.set(true);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected elegir(p: PersonaTramite): void {
    this.persona.set(p);
    this.aviso.set(null);
    this.api.perfilTramite(p.usuarioId).subscribe({ next: (x) => this.perfil.set(x), error: (e) => this.error.set(mensajeDeError(e)) });
    this.api.productosTramite(p.usuarioId).subscribe({ next: (x) => this.productos.set(x), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected reiniciar(): void {
    this.persona.set(null);
    this.perfil.set(null);
    this.productos.set(null);
    this.simulacion.set(null);
    this.datosListos.set(false);
    this.importe = null;
    this.plazo = null;
    this.origen = '';
    this.pdfs = [];
  }

  protected confirmarDatos(p: Perfil): void {
    this.perfil.set(p);
    this.datosListos.set(true);
  }

  protected elegirPdf(evento: Event): void {
    this.pdfs = Array.from((evento.target as HTMLInputElement).files ?? []);
  }

  protected simular(): void {
    this.error.set(null);
    this.api.simularTramite(this.persona()!.usuarioId, this.modalidad, this.importe!, this.plazo!).subscribe({
      next: (s) => this.simulacion.set(s),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected letras(): void {
    const p = this.persona()!;
    this.registrar(this.api.letrasTramite(p.usuarioId, this.modalidad, this.importe!, this.plazo!, this.origen), true);
  }

  protected emergente(): void {
    const p = this.persona()!;
    this.registrar(this.api.emergenteTramite(p.usuarioId, this.importe!, this.clave), !p.socio);
  }

  /** Si la subida del PDF falla, la solicitud ya quedó: se puede subir después desde la lista de créditos. */
  private registrar(accion: ReturnType<ApiService['letrasTramite']>, conDocumentos: boolean): void {
    const archivos = this.pdfs;
    const nombre = this.persona()!.nombre;
    this.enviando.set(true);
    this.error.set(null);
    accion.pipe(
      switchMap((r) => (conDocumentos && archivos.length ? this.api.documentosTramite(r.id, archivos) : of(null))),
    ).subscribe({
      next: () => {
        this.clave = crypto.randomUUID();
        this.enviando.set(false);
        this.reiniciar();
        this.aviso.set(`Solicitud de ${nombre} registrada. Ahora necesita los votos de presidencia o vicepresidencia, tesorería y comité de crédito.`);
        this.registrado.emit();
      },
      error: (e) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }
}
