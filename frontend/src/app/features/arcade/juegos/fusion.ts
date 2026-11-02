import { DERECHA, fondo, IZQUIERDA, Juego, texto } from '../motor';

const COLORES: Record<number, string> = {
  2: '#2c4a3e', 4: '#336b4f', 8: '#3f8a5c', 16: '#4fa868', 32: '#86b84a', 64: '#b9b23f',
  128: '#d49a3a', 256: '#d9783c', 512: '#d9604c', 1024: '#c24d7a', 2048: '#8b5cc4',
};

/**
 * Fusión: una fila de fichas que se empujan a la izquierda o a la derecha; dos iguales que se tocan se suman en una.
 * Los puntos son la suma de cada fusión. Se acaba cuando la fila se llena y no queda nada que sumar.
 */
export class Fusion extends Juego {
  private readonly casillas: number;
  private fila: number[];
  private destello: number[];
  private inicioX: number | null = null;

  constructor(ancho: number, alto: number) {
    super(ancho, alto);
    this.casillas = Math.max(4, Math.min(12, Math.floor((ancho - 60) / (alto - 3))));
    this.fila = new Array(this.casillas).fill(0);
    this.destello = new Array(this.casillas).fill(0);
    this.aparecer();
    this.aparecer();
  }

  private get lado(): number {
    return this.alto - 6;
  }

  private get origen(): number {
    return 4;
  }

  actualizar(dt: number): void {
    this.destello = this.destello.map((d) => Math.max(0, d - dt * 4));
  }

  private mover(haciaDerecha: boolean): void {
    const orden = haciaDerecha ? [...this.fila].reverse() : [...this.fila];
    const fichas = orden.filter((v) => v);
    const resultado: number[] = [];
    const sumadas: number[] = [];
    for (let i = 0; i < fichas.length; i++) {
      if (fichas[i] === fichas[i + 1]) {
        resultado.push(fichas[i] * 2);
        sumadas.push(resultado.length - 1);
        this.puntos += fichas[i] * 2;
        i++;
      } else {
        resultado.push(fichas[i]);
      }
    }
    while (resultado.length < this.casillas) {
      resultado.push(0);
    }
    const nueva = haciaDerecha ? resultado.reverse() : resultado;
    if (nueva.every((v, i) => v === this.fila[i])) {
      return;
    }
    this.fila = nueva;
    for (const i of sumadas) {
      this.destello[haciaDerecha ? this.casillas - 1 - i : i] = 1;
    }
    this.aparecer();
    this.terminado = !this.fila.includes(0) && !this.fila.some((v, i) => v === this.fila[i + 1]);
  }

  private aparecer(): void {
    const libres = this.fila.map((v, i) => (v ? -1 : i)).filter((i) => i >= 0);
    if (libres.length) {
      this.fila[libres[Math.floor(Math.random() * libres.length)]] = Math.random() < 0.9 ? 2 : 4;
    }
  }

  dibujar(c: CanvasRenderingContext2D): void {
    fondo(c, this.ancho, this.alto, '#141d1a');
    const l = this.lado;
    this.fila.forEach((valor, i) => {
      const crece = this.destello[i] * 2;
      const x = this.origen + i * (l + 3) - crece;
      c.fillStyle = valor ? (COLORES[valor] ?? '#6d3fb0') : '#1f2b27';
      c.beginPath();
      c.roundRect(x, 3 - crece, l + crece * 2, l + crece * 2, 4);
      c.fill();
      if (valor) {
        texto(c, String(valor), x + l / 2 + crece, this.alto / 2, '#f4f7f5', valor >= 1000 ? 9 : 11);
      }
    });
  }

  override tecla(codigo: string, abajo: boolean): boolean {
    const derecha = DERECHA.includes(codigo);
    if (!derecha && !IZQUIERDA.includes(codigo)) {
      return false;
    }
    if (abajo) {
      this.mover(derecha);
    }
    return true;
  }

  /** Deslizar hacia un lado empuja hacia ese lado; un toque sin deslizar empuja hacia la mitad tocada. */
  override puntero(x: number, _y: number, abajo: boolean): void {
    if (abajo) {
      this.inicioX = x;
      return;
    }
    // Soltar sin haber pulsado (por ejemplo, al pausar) no mueve nada.
    if (this.inicioX === null) {
      return;
    }
    const recorrido = x - this.inicioX;
    this.inicioX = null;
    this.mover(Math.abs(recorrido) > 12 ? recorrido > 0 : x > this.ancho / 2);
  }
}
