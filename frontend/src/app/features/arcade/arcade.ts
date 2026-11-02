import { afterNextRender, Component, computed, DestroyRef, ElementRef, inject, Injector, NgZone, signal, viewChild } from '@angular/core';
import { Gamepad2, LucideAngularModule, Play, RotateCcw, SkipForward, Trophy, X } from 'lucide-angular';

import { elegirJuego, FichaJuego, JUEGOS } from './catalogo';
import { MarcasArcade, Resultado } from './marcas';
import { ABAJO, ACCION, ARRIBA, ClaseJuego, DERECHA, IZQUIERDA, Juego, texto } from './motor';

const CLAVE_ANTERIOR = 'janery-arcade-anterior';
const TECLAS_DE_JUEGO = new Set([...ARRIBA, ...ABAJO, ...IZQUIERDA, ...DERECHA, ...ACCION]);

/**
 * Janery Arcade: un minijuego al azar en el hueco libre de la cabecera, sin cambiar su altura. Al cargar la página
 * elige uno distinto del de la visita anterior y no lo cambia mientras se juega. Solo se descarga el juego elegido; el
 * bucle y los eventos corren fuera de Angular y se limpian al salir.
 */
@Component({
  selector: 'app-arcade',
  imports: [LucideAngularModule],
  host: { class: 'arcade' },
  template: `
    @switch (estado()) {
      @case ('jugando') {
        <canvas #lienzo class="arcade-lienzo" tabindex="0" [attr.aria-label]="ficha().nombre + '. ' + ficha().controles + '. Escape para salir.'"></canvas>
        <button type="button" class="arcade-boton arcade-salir" title="Salir del juego" aria-label="Salir del juego" (click)="salir()">
          <lucide-icon [img]="iconos.X" [size]="14" aria-hidden="true" />
        </button>
      }
      @case ('resultado') {
        <div class="arcade-resultado" role="status">
          <span class="arcade-nombre">{{ ficha().nombre }}</span>
          <strong>{{ formato(resultado()!.puntos) }}</strong>
          @if (resultado()!.nuevoRecord) {
            <span class="arcade-nuevo"><lucide-icon [img]="iconos.Trophy" [size]="12" aria-hidden="true" /><span class="sm:hidden">¡Récord!</span><span class="hidden sm:inline">¡Nuevo récord!</span></span>
          } @else if (resultado()!.mejor !== null) {
            <span class="arcade-record">Récord {{ formato(resultado()!.mejor!) }}</span>
          }
        </div>
        <button type="button" class="arcade-boton" title="Jugar otra vez" aria-label="Jugar otra vez" (click)="jugar()">
          <lucide-icon [img]="iconos.RotateCcw" [size]="14" aria-hidden="true" />
        </button>
        <button type="button" class="arcade-boton" title="Otro juego" aria-label="Otro juego" (click)="siguiente()">
          <lucide-icon [img]="iconos.SkipForward" [size]="14" aria-hidden="true" />
        </button>
      }
      @default {
        <button type="button" class="arcade-invitacion" [disabled]="estado() === 'cargando'" [title]="ficha().controles" (click)="jugar()">
          <span class="arcade-etiqueta"><lucide-icon [img]="iconos.Gamepad2" [size]="15" aria-hidden="true" /></span>
          <span class="arcade-nombre">{{ ficha().nombre }}</span>
          <span class="arcade-mensaje">{{ estado() === 'cargando' ? 'Cargando…' : ficha().invitacion }}</span>
          @if (mejor() !== null) {
            <span class="arcade-record"><lucide-icon [img]="iconos.Trophy" [size]="12" aria-hidden="true" /> {{ formato(mejor()!) }}</span>
          }
          <span class="arcade-jugar"><lucide-icon [img]="iconos.Play" [size]="13" aria-hidden="true" /><span class="sr-only">Jugar</span></span>
        </button>
        <button type="button" class="arcade-boton" title="Otro juego" aria-label="Otro juego" (click)="siguiente()">
          <lucide-icon [img]="iconos.SkipForward" [size]="14" aria-hidden="true" />
        </button>
      }
    }
  `,
})
export class Arcade {
  private readonly zona = inject(NgZone);
  private readonly injector = inject(Injector);
  private readonly marcas = inject(MarcasArcade);
  private readonly lienzo = viewChild<ElementRef<HTMLCanvasElement>>('lienzo');

  protected readonly iconos = { Gamepad2, Play, RotateCcw, SkipForward, Trophy, X };
  protected readonly ficha = signal<FichaJuego>(this.elegir(leerAnterior()));
  protected readonly estado = signal<'invitacion' | 'cargando' | 'jugando' | 'resultado'>('invitacion');
  protected readonly resultado = signal<Resultado | null>(null);
  private readonly version = signal(0);
  protected readonly mejor = computed(() => (this.version(), this.marcas.leer(this.ficha().id).mejor));

  private partida: AbortController | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.detener());
  }

  protected formato(puntos: number): string {
    return `${puntos} ${this.ficha().unidad ?? 'pts'}`;
  }

  protected siguiente(): void {
    this.detener();
    this.ficha.set(this.elegir(this.ficha().id));
    this.estado.set('invitacion');
  }

  protected salir(): void {
    this.detener();
    this.estado.set('invitacion');
  }

  protected async jugar(): Promise<void> {
    this.detener();
    const ficha = this.ficha();
    this.estado.set('cargando');
    let Clase: ClaseJuego;
    try {
      Clase = await ficha.cargar();
    } catch {
      this.estado.set('invitacion');
      return;
    }
    // Pudo cambiarse de juego mientras se descargaba.
    if (this.ficha() !== ficha || this.estado() !== 'cargando') {
      return;
    }
    this.estado.set('jugando');
    afterNextRender({ write: () => this.iniciar(ficha, (ancho, alto) => new Clase(ancho, alto)) }, { injector: this.injector });
  }

  private iniciar(ficha: FichaJuego, crear: (ancho: number, alto: number) => Juego): void {
    const lienzo = this.lienzo()?.nativeElement;
    const contexto = lienzo?.getContext('2d');
    if (!lienzo || !contexto) {
      return;
    }
    const partida = new AbortController();
    this.partida = partida;
    const senal = { signal: partida.signal };

    let ancho = lienzo.clientWidth;
    let alto = lienzo.clientHeight;
    const ajustar = () => {
      const escala = window.devicePixelRatio || 1;
      lienzo.width = Math.round(ancho * escala);
      lienzo.height = Math.round(alto * escala);
      contexto.setTransform(escala, 0, 0, escala, 0, 0);
    };
    ajustar();
    const juego = crear(ancho, alto);

    let cuadro = 0;
    let previo = 0;
    let jugado = 0;
    let pausado = false;
    const pulsadas = new Set<string>();

    const dibujar = () => {
      juego.dibujar(contexto);
      if (ficha.marcador !== false) {
        const t = String(juego.puntos);
        contexto.fillStyle = 'rgba(0, 0, 0, 0.35)';
        contexto.beginPath();
        const w = 10 + t.length * 6;
        contexto.roundRect(ancho - 3 - w, 3, w, 14, 7);
        contexto.fill();
        texto(contexto, t, ancho - 3 - w / 2, 10.5, '#f4f7f5', 10);
      }
      if (pausado) {
        contexto.fillStyle = 'rgba(8, 14, 12, 0.72)';
        contexto.fillRect(0, 0, ancho, alto);
        texto(contexto, 'En pausa · toque para seguir', ancho / 2, alto / 2, '#e2ebe6', 11);
      }
    };

    const paso = (ahora: number) => {
      // Tras una pausa o una pestaña oculta no se salta hacia adelante: como mucho 50 ms por cuadro.
      const dt = previo ? Math.min(0.05, (ahora - previo) / 1000) : 0;
      previo = ahora;
      jugado += dt;
      juego.actualizar(dt);
      dibujar();
      if (juego.terminado) {
        this.zona.run(() => this.terminar(ficha, juego.puntos, jugado));
        return;
      }
      cuadro = requestAnimationFrame(paso);
    };

    const pausar = () => {
      if (pausado) {
        return;
      }
      pausado = true;
      cancelAnimationFrame(cuadro);
      for (const codigo of pulsadas) {
        juego.tecla(codigo, false);
      }
      pulsadas.clear();
      juego.puntero(0, 0, false);
      dibujar();
    };
    const seguir = () => {
      if (!pausado) {
        return;
      }
      pausado = false;
      previo = 0;
      cuadro = requestAnimationFrame(paso);
    };

    const posicion = (e: PointerEvent) => {
      const caja = lienzo.getBoundingClientRect();
      return [e.clientX - caja.left, e.clientY - caja.top] as const;
    };

    this.zona.runOutsideAngular(() => {
      lienzo.addEventListener('keydown', (e) => {
        if (e.code === 'Escape') {
          e.preventDefault();
          this.zona.run(() => this.salir());
          return;
        }
        if (pausado) {
          if (TECLAS_DE_JUEGO.has(e.code)) {
            e.preventDefault();
            seguir();
          }
          return;
        }
        // Las repeticiones de una tecla mantenida no cuentan: un salto por pulsación.
        if (e.repeat ? TECLAS_DE_JUEGO.has(e.code) : juego.tecla(e.code, true)) {
          e.preventDefault();
          pulsadas.add(e.code);
        }
      }, senal);
      lienzo.addEventListener('keyup', (e) => {
        if (juego.tecla(e.code, false)) {
          e.preventDefault();
        }
        pulsadas.delete(e.code);
      }, senal);
      lienzo.addEventListener('pointerdown', (e) => {
        e.preventDefault();
        const estabaPausado = pausado;
        lienzo.focus({ preventScroll: true });
        if (estabaPausado) {
          seguir();
          return;
        }
        lienzo.setPointerCapture(e.pointerId);
        juego.puntero(...posicion(e), true);
      }, senal);
      lienzo.addEventListener('pointermove', (e) => juego.moverPuntero(...posicion(e)), senal);
      lienzo.addEventListener('pointerup', (e) => juego.puntero(...posicion(e), false), senal);
      lienzo.addEventListener('pointercancel', (e) => juego.puntero(...posicion(e), false), senal);
      lienzo.addEventListener('contextmenu', (e) => e.preventDefault(), senal);
      lienzo.addEventListener('blur', pausar, senal);
      lienzo.addEventListener('focus', seguir, senal);

      const observador = new ResizeObserver(() => {
        if (lienzo.clientWidth && lienzo.clientHeight) {
          ancho = lienzo.clientWidth;
          alto = lienzo.clientHeight;
          ajustar();
          juego.redimensionar(ancho, alto);
          dibujar();
        }
      });
      observador.observe(lienzo);
      partida.signal.addEventListener('abort', () => {
        cancelAnimationFrame(cuadro);
        observador.disconnect();
      });

      lienzo.focus({ preventScroll: true });
      cuadro = requestAnimationFrame(paso);
    });
  }

  private terminar(ficha: FichaJuego, puntos: number, segundos: number): void {
    this.detener();
    this.resultado.set(this.marcas.anotar(ficha, puntos, segundos));
    this.version.update((v) => v + 1);
    this.estado.set('resultado');
  }

  /** Corta el bucle y quita todos los eventos de la partida en curso. */
  private detener(): void {
    this.partida?.abort();
    this.partida = null;
  }

  private elegir(anterior: string | null): FichaJuego {
    const ficha = elegirJuego(JUEGOS, anterior);
    try {
      sessionStorage.setItem(CLAVE_ANTERIOR, ficha.id);
    } catch {
      // Sin almacenamiento: puede repetirse al recargar, nada más.
    }
    return ficha;
  }
}

function leerAnterior(): string | null {
  try {
    return sessionStorage.getItem(CLAVE_ANTERIOR);
  } catch {
    return null;
  }
}
