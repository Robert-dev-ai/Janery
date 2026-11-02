import { ABAJO, ARRIBA, Caja, circulo, fondo, Juego, limitar } from '../motor';

/**
 * Ladrillos, en horizontal: la paleta va a la izquierda y el muro a la derecha. Cada ladrillo suma un punto; al
 * terminar el muro aparece otro, más rápido. Hay tres pelotas.
 */
export class Ladrillos extends Juego {
  private paleta = this.alto / 2;
  private bola = { x: 0, y: 0, vx: 0, vy: 0 };
  private ladrillos: (Caja & { color: string })[] = [];
  private vidas = 3;
  private muro = 0;
  private subir = false;
  private bajar = false;
  private objetivo: number | null = null;
  private pausa = 0;

  constructor(ancho: number, alto: number) {
    super(ancho, alto);
    this.levantarMuro();
    this.sacar();
  }

  private get largo(): number {
    return this.alto * 0.42;
  }

  private levantarMuro(): void {
    const filas = 4;
    const columnas = Math.max(3, Math.min(8, Math.floor(this.ancho / 60)));
    const alto = (this.alto - 2) / filas;
    const ancho = 9;
    const inicio = this.ancho - 6 - columnas * (ancho + 2);
    const colores = ['#e07a5f', '#e9b949', '#81b29a', '#5fa8d3'];
    this.ladrillos = [];
    for (let col = 0; col < columnas; col++) {
      for (let fila = 0; fila < filas; fila++) {
        this.ladrillos.push({ x: inicio + col * (ancho + 2), y: 1 + fila * alto + 0.5, w: ancho, h: alto - 1, color: colores[col % colores.length] });
      }
    }
    this.muro++;
  }

  private sacar(): void {
    const rapidez = Math.max(140, this.ancho * 0.3) * (1 + (this.muro - 1) * 0.12);
    this.bola = { x: 14, y: this.paleta, vx: rapidez, vy: (Math.random() < 0.5 ? -1 : 1) * this.alto * 1.6 };
    this.pausa = 0.5;
  }

  actualizar(dt: number): void {
    const mitad = this.largo / 2;
    const rapidezPaleta = this.alto * 5;
    if (this.objetivo !== null) {
      this.paleta += limitar(this.objetivo - this.paleta, -rapidezPaleta * dt * 2, rapidezPaleta * dt * 2);
    }
    this.paleta = limitar(this.paleta + ((this.bajar ? 1 : 0) - (this.subir ? 1 : 0)) * rapidezPaleta * dt, mitad, this.alto - mitad);

    if (this.pausa > 0) {
      this.pausa -= dt;
      this.bola.y = this.paleta;
      return;
    }
    const b = this.bola;
    const r = 2.5;
    b.x += b.vx * dt;
    b.y += b.vy * dt;
    if (b.y < r || b.y > this.alto - r) {
      b.y = limitar(b.y, r, this.alto - r);
      b.vy = -b.vy;
    }
    if (b.x > this.ancho - r) {
      b.x = this.ancho - r;
      b.vx = -Math.abs(b.vx);
    }
    if (b.vx < 0 && b.x - r <= 8 && b.x > 2 && Math.abs(b.y - this.paleta) <= mitad + r) {
      b.vx = Math.abs(b.vx);
      b.vy = ((b.y - this.paleta) / mitad) * this.alto * 3;
      b.x = 8 + r;
    }

    const golpeado = this.ladrillos.findIndex((l) => b.x + r > l.x && b.x - r < l.x + l.w && b.y + r > l.y && b.y - r < l.y + l.h);
    if (golpeado >= 0) {
      const l = this.ladrillos[golpeado];
      // Rebota por el lado en que entró: de frente invierte la marcha; por arriba o abajo, la subida.
      const entradaLateral = Math.min(Math.abs(b.x + r - l.x), Math.abs(l.x + l.w - (b.x - r))) < Math.min(Math.abs(b.y + r - l.y), Math.abs(l.y + l.h - (b.y - r)));
      if (entradaLateral) {
        b.vx = -b.vx;
      } else {
        b.vy = -b.vy;
      }
      this.ladrillos.splice(golpeado, 1);
      this.puntos++;
      if (!this.ladrillos.length) {
        this.levantarMuro();
        this.sacar();
      }
    }

    if (b.x < 0) {
      this.vidas--;
      if (this.vidas <= 0) {
        this.terminado = true;
      } else {
        this.sacar();
      }
    }
  }

  dibujar(c: CanvasRenderingContext2D): void {
    fondo(c, this.ancho, this.alto, '#161a24');
    for (const l of this.ladrillos) {
      c.fillStyle = l.color;
      c.fillRect(l.x, l.y, l.w, l.h);
    }
    c.fillStyle = '#4fd88a';
    c.fillRect(4, this.paleta - this.largo / 2, 4, this.largo);
    circulo(c, this.bola.x, this.bola.y, 2.5, '#f3f4f6');
    for (let i = 0; i < this.vidas; i++) {
      circulo(c, 14 + i * 6, this.alto - 4, 1.8, '#9fb0c4');
    }
  }

  override tecla(codigo: string, abajo: boolean): boolean {
    if (ARRIBA.includes(codigo)) {
      this.subir = abajo;
    } else if (ABAJO.includes(codigo)) {
      this.bajar = abajo;
    } else {
      return false;
    }
    this.objetivo = null;
    return true;
  }

  override puntero(_x: number, y: number, abajo: boolean): void {
    if (abajo) {
      this.objetivo = y;
    }
  }

  override moverPuntero(_x: number, y: number): void {
    this.objetivo = y;
  }
}
