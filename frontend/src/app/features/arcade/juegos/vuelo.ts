import { ACCION, ARRIBA, azar, circulo, fondo, Juego } from '../motor';

interface Columna {
  x: number;
  hueco: number;
  pasada: boolean;
}

/** Vuelo: cada toque impulsa a la alcancía; hay que pasar por los huecos de las columnas. */
export class Vuelo extends Juego {
  private y = this.alto / 2;
  private vy = 0;
  private columnas: Columna[] = [];
  private hastaSiguiente = 0;
  private readonly velocidad = 95;
  private readonly grosor = 10;

  private get x(): number {
    return Math.min(60, this.ancho * 0.2);
  }

  private get radio(): number {
    return this.alto * 0.13;
  }

  private get anchoHueco(): number {
    return this.alto * 0.56;
  }

  actualizar(dt: number): void {
    this.vy += this.alto * 11 * dt;
    this.y += this.vy * dt;
    if (this.y < this.radio) {
      this.y = this.radio;
      this.vy = 0;
    }

    const avance = this.velocidad * dt;
    for (const col of this.columnas) {
      col.x -= avance;
      if (!col.pasada && col.x + this.grosor < this.x) {
        col.pasada = true;
        this.puntos++;
      }
    }
    this.columnas = this.columnas.filter((col) => col.x + this.grosor > 0);
    this.hastaSiguiente -= avance;
    if (this.hastaSiguiente <= 0) {
      const margen = this.anchoHueco / 2 + 1;
      this.columnas.push({ x: this.ancho + 2, hueco: azar(margen, this.alto - margen), pasada: false });
      this.hastaSiguiente = azar(95, 130);
    }

    const r = this.radio * 0.85;
    const golpe = this.columnas.some(
      (col) => this.x + r > col.x && this.x - r < col.x + this.grosor && Math.abs(this.y - col.hueco) > this.anchoHueco / 2 - r,
    );
    this.terminado = golpe || this.y + this.radio >= this.alto;
  }

  dibujar(c: CanvasRenderingContext2D): void {
    fondo(c, this.ancho, this.alto, '#0f2233');
    c.fillStyle = '#3fa06a';
    for (const col of this.columnas) {
      const arriba = col.hueco - this.anchoHueco / 2;
      const abajo = col.hueco + this.anchoHueco / 2;
      c.fillRect(col.x, 0, this.grosor, arriba);
      c.fillRect(col.x, abajo, this.grosor, this.alto - abajo);
    }
    // Alcancía: cuerpo, oreja y ranura.
    const inclinacion = Math.max(-0.4, Math.min(0.5, this.vy / (this.alto * 8)));
    c.save();
    c.translate(this.x, this.y);
    c.rotate(inclinacion);
    circulo(c, 0, 0, this.radio, '#f3a6b8');
    circulo(c, this.radio * 0.75, this.radio * 0.1, this.radio * 0.38, '#e98aa2');
    c.fillStyle = '#b85a73';
    c.fillRect(-this.radio * 0.3, -this.radio * 0.75, this.radio * 0.6, 1.2);
    c.restore();
  }

  override tecla(codigo: string, abajo: boolean): boolean {
    if (![...ACCION, ...ARRIBA].includes(codigo)) {
      return false;
    }
    if (abajo) {
      this.impulsar();
    }
    return true;
  }

  override puntero(_x: number, _y: number, abajo: boolean): void {
    if (abajo) {
      this.impulsar();
    }
  }

  private impulsar(): void {
    this.vy = -this.alto * 3.1;
  }
}
