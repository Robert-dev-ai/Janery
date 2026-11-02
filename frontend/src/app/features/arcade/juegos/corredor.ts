import { ACCION, ARRIBA, azar, Caja, choca, fondo, Juego, moneda } from '../motor';

/** Carrera: la moneda corre sola y salta los obstáculos del camino. */
export class Corredor extends Juego {
  private y = 0;
  private vy = 0;
  private velocidad = 150;
  private recorrido = 0;
  private hastaSiguiente = 120;
  private obstaculos: Caja[] = [];

  private get suelo(): number {
    return this.alto - 4;
  }

  private get radio(): number {
    return this.alto * 0.17;
  }

  /** Gravedad y empuje para un salto de ~55 % del alto que dura medio segundo. */
  private get gravedad(): number {
    return (8 * this.alto * 0.55) / 0.5 ** 2;
  }

  actualizar(dt: number): void {
    this.velocidad = Math.min(380, this.velocidad + 5 * dt);
    const avance = this.velocidad * dt;
    this.recorrido += avance;
    this.puntos = Math.floor(this.recorrido / 10);

    this.vy += this.gravedad * dt;
    this.y = Math.min(0, this.y + this.vy * dt);
    if (this.y === 0) {
      this.vy = 0;
    }

    for (const o of this.obstaculos) {
      o.x -= avance;
    }
    this.obstaculos = this.obstaculos.filter((o) => o.x + o.w > 0);
    this.hastaSiguiente -= avance;
    if (this.hastaSiguiente <= 0) {
      this.agregarObstaculo();
    }

    const r = this.radio * 0.8;
    const jugador = { x: 28 - r, y: this.suelo - this.radio + this.y - r, w: r * 2, h: r * 2 };
    this.terminado = this.obstaculos.some((o) => choca(jugador, o));
  }

  private agregarObstaculo(): void {
    const alto = this.alto * azar(0.22, 0.42);
    const ancho = azar(5, 9);
    this.obstaculos.push({ x: this.ancho + 4, y: this.suelo - alto, w: ancho, h: alto });
    if (Math.random() < 0.25) {
      this.obstaculos.push({ x: this.ancho + 4 + ancho + 2, y: this.suelo - alto * 0.7, w: ancho, h: alto * 0.7 });
    }
    // Siempre queda espacio para caer y volver a saltar.
    this.hastaSiguiente = this.velocidad * azar(0.75, 1.6);
  }

  dibujar(c: CanvasRenderingContext2D): void {
    fondo(c, this.ancho, this.alto, '#10201a');
    c.fillStyle = '#2f4a3f';
    c.fillRect(0, this.suelo, this.ancho, 1);
    c.fillStyle = '#1f3a2f';
    const paso = 34;
    for (let x = -(this.recorrido % paso); x < this.ancho; x += paso) {
      c.fillRect(x, this.suelo + 2, 8, 1);
    }
    c.fillStyle = '#d9604c';
    for (const o of this.obstaculos) {
      c.fillRect(o.x, o.y, o.w, o.h);
    }
    moneda(c, 28, this.suelo - this.radio + this.y, this.radio);
  }

  override tecla(codigo: string, abajo: boolean): boolean {
    if (![...ACCION, ...ARRIBA].includes(codigo)) {
      return false;
    }
    if (abajo) {
      this.saltar();
    }
    return true;
  }

  override puntero(_x: number, _y: number, abajo: boolean): void {
    if (abajo) {
      this.saltar();
    }
  }

  private saltar(): void {
    if (this.y === 0) {
      this.vy = -Math.sqrt(2 * this.gravedad * this.alto * 0.55);
    }
  }
}
