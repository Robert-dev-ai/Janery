import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { AuthService, Yo } from '../../core/auth/auth';
import { Arcade } from './arcade';
import { elegirJuego, esCreible, JUEGOS } from './catalogo';
import { MarcasArcade } from './marcas';
import { ClaseJuego } from './motor';

describe('Janery Arcade', () => {
  it('tiene diez juegos con identificador único', () => {
    expect(JUEGOS.length).toBe(10);
    expect(new Set(JUEGOS.map((j) => j.id)).size).toBe(10);
  });

  it('nunca repite el juego anterior', () => {
    for (const anterior of JUEGOS) {
      for (let i = 0; i < 20; i++) {
        expect(elegirJuego(JUEGOS, anterior.id, () => i / 20).id).not.toBe(anterior.id);
      }
    }
  });

  it('con un solo juego, lo repite', () => {
    expect(elegirJuego([JUEGOS[0]], JUEGOS[0].id).id).toBe(JUEGOS[0].id);
  });

  it('descarta marcas imposibles', () => {
    const carrera = JUEGOS.find((j) => j.id === 'corredor')!;
    const reflejos = JUEGOS.find((j) => j.id === 'reflejos')!;
    expect(esCreible(carrera, 300, 10)).toBeTrue();
    expect(esCreible(carrera, 99_999, 10)).toBeFalse();
    expect(esCreible(carrera, 1.5, 10)).toBeFalse();
    expect(esCreible(carrera, -1, 10)).toBeFalse();
    expect(esCreible(reflejos, 240, 5)).toBeTrue();
    expect(esCreible(reflejos, 20, 5)).toBeFalse();
  });

  // Cada juego se crea, avanza y se dibuja sin errores durante unos segundos simulados.
  for (const ficha of JUEGOS) {
    it(`${ficha.nombre} avanza y se dibuja dentro de la barra`, async () => {
      const Clase: ClaseJuego = await ficha.cargar();
      const lienzo = document.createElement('canvas');
      const contexto = lienzo.getContext('2d')!;
      for (const ancho of [140, 600]) {
        const juego = new Clase(ancho, 36);
        for (let i = 0; i < 300 && !juego.terminado; i++) {
          if (i % 25 === 0) {
            juego.tecla('Space', true);
            juego.puntero(ancho / 2, 18, true);
          }
          juego.tecla('Space', false);
          juego.puntero(ancho / 2, 18, false);
          juego.actualizar(1 / 60);
          juego.dibujar(contexto);
        }
        expect(Number.isFinite(juego.puntos)).toBeTrue();
      }
    });
  }

  it('pasa de la invitación a la partida y vuelve con Escape, siempre en la misma barra', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient()] });
    const fixture = TestBed.createComponent(Arcade);
    fixture.detectChanges();
    const barra: HTMLElement = fixture.nativeElement;
    const nombre = barra.querySelector('.arcade-nombre')!.textContent;
    expect(JUEGOS.some((j) => j.nombre === nombre)).toBeTrue();

    (barra.querySelector('.arcade-invitacion') as HTMLButtonElement).click();
    // El juego se descarga aparte: se espera a que aparezca su lienzo.
    for (let i = 0; i < 50 && !barra.querySelector('canvas'); i++) {
      await new Promise((r) => setTimeout(r, 20));
      fixture.detectChanges();
    }
    const lienzo = barra.querySelector('canvas')!;
    expect(lienzo).toBeTruthy();

    lienzo.dispatchEvent(new KeyboardEvent('keydown', { code: 'Escape' }));
    fixture.detectChanges();
    expect(barra.querySelector('canvas')).toBeNull();
    expect(barra.querySelector('.arcade-nombre')!.textContent).toBe(nombre);

    (barra.querySelector('button[aria-label="Otro juego"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(barra.querySelector('.arcade-nombre')!.textContent).not.toBe(nombre);
    fixture.destroy();
  });

  describe('récords', () => {
    let marcas: MarcasArcade;

    beforeEach(() => {
      TestBed.configureTestingModule({ providers: [provideHttpClient()] });
      TestBed.inject(AuthService).yo.set({ id: 9001 } as Yo);
      localStorage.removeItem('janery-arcade-9001');
      marcas = TestBed.inject(MarcasArcade);
    });

    afterEach(() => localStorage.removeItem('janery-arcade-9001'));

    it('guarda el mejor resultado de cada persona', () => {
      const carrera = JUEGOS.find((j) => j.id === 'corredor')!;
      expect(marcas.anotar(carrera, 50, 10).nuevoRecord).toBeTrue();
      expect(marcas.anotar(carrera, 30, 10).nuevoRecord).toBeFalse();
      expect(marcas.leer('corredor').mejor).toBe(50);
      expect(marcas.leer('corredor').partidas.length).toBe(2);

      TestBed.inject(AuthService).yo.set({ id: 9002 } as Yo);
      expect(marcas.leer('corredor').mejor).toBeNull();
    });

    it('en Reflejos gana el tiempo más bajo', () => {
      const reflejos = JUEGOS.find((j) => j.id === 'reflejos')!;
      marcas.anotar(reflejos, 300, 5);
      expect(marcas.anotar(reflejos, 250, 5).nuevoRecord).toBeTrue();
      expect(marcas.leer('reflejos').mejor).toBe(250);
    });

    it('no guarda una marca manipulada', () => {
      const carrera = JUEGOS.find((j) => j.id === 'corredor')!;
      expect(marcas.anotar(carrera, 1_000_000, 3).nuevoRecord).toBeFalse();
      expect(marcas.leer('corredor').mejor).toBeNull();
    });
  });
});
