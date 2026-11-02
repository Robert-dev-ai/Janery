import { ACCION, ARRIBA, azar, Caja, choca, fondo, Juego, limitar } from '../motor';

interface Tramo {
  x: number;
  techo: number;
  piso: number;
}

/**
 * Túnel: mientras se mantiene pulsado, el helicóptero sube; al soltar, baja. El túnel se estrecha con la distancia y
 * aparecen bloques sueltos. Los puntos son la distancia recorrida.
 */
export class Tunel extends Juego {
  private y = this.alto / 2;
  private vy = 0;
  private subiendo = false;
  private recorrido = 0;
  private velocidad = 120;
  private tramos: Tramo[] = [];
  private bloques: Caja[] = [];
  private hastaBloque = 260;
  private helice = 0;
  private readonly paso = 8;

  constructor(ancho: number, alto: number) {
    super(ancho, alto);
    for (let x = 0; x < ancho + this.paso; x += this.paso) {
      this.tramos.push({ x, techo: 2, piso: alto - 2 });
    }
  }

  private get x(): number {
    return Math.min(50, this.ancho * 0.18);
  }

  actualizar(dt: number): void {
    this.velocidad = Math.min(260, this.velocidad + 3 * dt);
    const avance = this.velocidad * dt;
    this.recorrido += avance;
    this.puntos = Math.floor(this.recorrido / 10);
    this.helice += dt * 30;

    const empuje = this.subiendo ? -this.alto * 9 : this.alto * 7;
    this.vy = limitar(this.vy + empuje * dt, -this.alto * 2.2, this.alto * 2.2);
    this.y += this.vy * dt;

    for (const t of this.tramos) {
      t.x -= avance;
    }
    for (const b of this.bloques) {
      b.x -= avance;
    }
    this.tramos = this.tramos.filter((t) => t.x + this.paso > 0);
    this.bloques = this.bloques.filter((b) => b.x + b.w > 0);
    while (this.tramos.at(-1)!.x < this.ancho) {
      this.agregarTramo();
    }
    this.hastaBloque -= avance;
    if (this.hastaBloque <= 0) {
      const t = this.tramos.at(-1)!;
      const alto = (t.piso - t.techo) * 0.3;
      this.bloques.push({ x: this.ancho, y: azar(t.techo, t.piso - alto), w: 6, h: alto });
      this.hastaBloque = azar(160, 300);
    }

    const casco = { x: this.x - 5, y: this.y - 3, w: 10, h: 6 };
    const tramo = this.tramos.find((t) => t.x <= this.x && t.x + this.paso > this.x);
    const contraPared = tramo ? casco.y < tramo.techo || casco.y + casco.h > tramo.piso : false;
    this.terminado = contraPared || this.bloques.some((b) => choca(casco, b));
  }

  /** Las paredes cambian poco a poco y el hueco nunca baja del 55 % del alto. */
  private agregarTramo(): void {
    const ultimo = this.tramos.at(-1)!;
    const hueco = this.alto * Math.max(0.55, 0.95 - this.recorrido / 8000);
    const centro = limitar((ultimo.techo + ultimo.piso) / 2 + azar(-2, 2), hueco / 2, this.alto - hueco / 2);
    this.tramos.push({ x: ultimo.x + this.paso, techo: centro - hueco / 2, piso: centro + hueco / 2 });
  }

  dibujar(c: CanvasRenderingContext2D): void {
    fondo(c, this.ancho, this.alto, '#0f1d1a');
    c.fillStyle = '#3c5c4c';
    for (const t of this.tramos) {
      c.fillRect(t.x, 0, this.paso + 0.5, t.techo);
      c.fillRect(t.x, t.piso, this.paso + 0.5, this.alto - t.piso);
    }
    c.fillStyle = '#7a8f86';
    for (const b of this.bloques) {
      c.fillRect(b.x, b.y, b.w, b.h);
    }
    // Helicóptero: cabina, cola y hélice que gira.
    c.fillStyle = '#e9b949';
    c.beginPath();
    c.ellipse(this.x, this.y, 5, 3, 0, 0, Math.PI * 2);
    c.fill();
    c.fillRect(this.x - 10, this.y - 1, 6, 1.5);
    c.fillStyle = '#d7e3dd';
    const aspa = Math.abs(Math.sin(this.helice)) * 6;
    c.fillRect(this.x - aspa, this.y - 4.5, aspa * 2, 1);
  }

  override tecla(codigo: string, abajo: boolean): boolean {
    if (![...ACCION, ...ARRIBA].includes(codigo)) {
      return false;
    }
    this.subiendo = abajo;
    return true;
  }

  override puntero(_x: number, _y: number, abajo: boolean): void {
    this.subiendo = abajo;
  }
}
