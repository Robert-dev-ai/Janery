import { ABAJO, ARRIBA, circulo, fondo, Juego, limitar } from '../motor';

/**
 * Rebote: la paleta propia a la izquierda y la del rival a la derecha. Cada devolución suma un punto y cada tanto al
 * rival, cinco; la partida acaba al dejar pasar la pelota.
 */
export class Rebote extends Juego {
  private propia = this.alto / 2;
  private rival = this.alto / 2;
  private bola = { x: 0, y: 0, vx: 0, vy: 0 };
  private subir = false;
  private bajar = false;
  private objetivo: number | null = null;
  private pausa = 0;

  constructor(ancho: number, alto: number) {
    super(ancho, alto);
    this.sacar(1);
  }

  private get largo(): number {
    return this.alto * 0.42;
  }

  private sacar(direccion: number): void {
    const rapidez = Math.max(150, this.ancho * 0.35);
    this.bola = { x: this.ancho / 2, y: this.alto / 2, vx: direccion * rapidez, vy: (Math.random() - 0.5) * this.alto * 2 };
    this.pausa = 0.5;
  }

  actualizar(dt: number): void {
    const mitad = this.largo / 2;
    const rapidezPaleta = this.alto * 5;
    if (this.objetivo !== null) {
      this.propia += limitar(this.objetivo - this.propia, -rapidezPaleta * dt * 2, rapidezPaleta * dt * 2);
    }
    this.propia += ((this.bajar ? 1 : 0) - (this.subir ? 1 : 0)) * rapidezPaleta * dt;
    this.propia = limitar(this.propia, mitad, this.alto - mitad);

    // El rival sigue la pelota con algo de retraso, para que se le pueda ganar.
    const destino = this.bola.vx > 0 ? this.bola.y : this.alto / 2;
    this.rival += limitar(destino - this.rival, -this.alto * 2.6 * dt, this.alto * 2.6 * dt);
    this.rival = limitar(this.rival, mitad, this.alto - mitad);

    if (this.pausa > 0) {
      this.pausa -= dt;
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
    if (b.vx < 0 && b.x - r <= 8 && b.x > 2 && Math.abs(b.y - this.propia) <= mitad + r) {
      this.devolver(this.propia, 1);
      this.puntos++;
    } else if (b.vx > 0 && b.x + r >= this.ancho - 8 && b.x < this.ancho - 2 && Math.abs(b.y - this.rival) <= mitad + r) {
      this.devolver(this.rival, -1);
    }
    if (b.x < 0) {
      this.terminado = true;
    } else if (b.x > this.ancho) {
      this.puntos += 5;
      this.sacar(-1);
    }
  }

  /** El ángulo depende de dónde pega en la paleta; cada golpe acelera un poco. */
  private devolver(paleta: number, direccion: number): void {
    const b = this.bola;
    const desvio = (b.y - paleta) / (this.largo / 2);
    b.vx = direccion * Math.min(Math.abs(b.vx) * 1.06, this.ancho * 1.4);
    b.vy = desvio * this.alto * 3;
    b.x = direccion > 0 ? 8 + 2.5 : this.ancho - 8 - 2.5;
  }

  dibujar(c: CanvasRenderingContext2D): void {
    fondo(c, this.ancho, this.alto, '#141b26');
    c.fillStyle = '#2b3646';
    for (let y = 2; y < this.alto; y += 6) {
      c.fillRect(this.ancho / 2 - 0.5, y, 1, 3);
    }
    const mitad = this.largo / 2;
    c.fillStyle = '#4fd88a';
    c.fillRect(4, this.propia - mitad, 4, this.largo);
    c.fillStyle = '#9fb0c4';
    c.fillRect(this.ancho - 8, this.rival - mitad, 4, this.largo);
    circulo(c, this.bola.x, this.bola.y, 2.5, '#f3f4f6');
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
    this.objetivo = abajo ? y : this.objetivo;
  }

  override moverPuntero(_x: number, y: number): void {
    this.objetivo = y;
  }
}
