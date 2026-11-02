/**
 * Base de los minijuegos de Janery Arcade. Cada juego es una clase sin Angular que solo avanza y se dibuja; la barra
 * se encarga del lienzo, el bucle de animación, las teclas y los toques. Las medidas son píxeles CSS del lienzo.
 */
export abstract class Juego {
  puntos = 0;
  terminado = false;

  constructor(
    protected ancho: number,
    protected alto: number,
  ) {}

  redimensionar(ancho: number, alto: number): void {
    this.ancho = ancho;
    this.alto = alto;
  }

  /** Avanza el juego; dt en segundos, ya acotado por la barra. */
  abstract actualizar(dt: number): void;

  abstract dibujar(c: CanvasRenderingContext2D): void;

  /** Devuelve true si la tecla pertenece al juego: así la barra evita que la página la use. */
  tecla(_codigo: string, _abajo: boolean): boolean {
    return false;
  }

  puntero(_x: number, _y: number, _abajo: boolean): void {}

  moverPuntero(_x: number, _y: number): void {}
}

export type ClaseJuego = new (ancho: number, alto: number) => Juego;

export const ARRIBA = ['ArrowUp', 'KeyW'];
export const ABAJO = ['ArrowDown', 'KeyS'];
export const IZQUIERDA = ['ArrowLeft', 'KeyA'];
export const DERECHA = ['ArrowRight', 'KeyD'];
export const ACCION = ['Space', 'Enter'];

export function azar(min: number, max: number): number {
  return min + Math.random() * (max - min);
}

export function limitar(valor: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, valor));
}

export function choca(a: Caja, b: Caja): boolean {
  return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
}

export interface Caja {
  x: number;
  y: number;
  w: number;
  h: number;
}

export function fondo(c: CanvasRenderingContext2D, ancho: number, alto: number, color: string): void {
  c.fillStyle = color;
  c.fillRect(0, 0, ancho, alto);
}

export function circulo(c: CanvasRenderingContext2D, x: number, y: number, r: number, color: string): void {
  c.fillStyle = color;
  c.beginPath();
  c.arc(x, y, r, 0, Math.PI * 2);
  c.fill();
}

export function texto(c: CanvasRenderingContext2D, t: string, x: number, y: number, color: string, tamano = 11, alinear: CanvasTextAlign = 'center'): void {
  c.fillStyle = color;
  c.font = `600 ${tamano}px Inter, "Segoe UI", system-ui, sans-serif`;
  c.textAlign = alinear;
  c.textBaseline = 'middle';
  c.fillText(t, x, y);
}

/** Moneda de Janery: el personaje de varios juegos. */
export function moneda(c: CanvasRenderingContext2D, x: number, y: number, r: number): void {
  circulo(c, x, y, r, '#e9b949');
  circulo(c, x, y, r * 0.62, '#f6d77a');
  c.fillStyle = '#b8862b';
  c.fillRect(x - r * 0.12, y - r * 0.42, r * 0.24, r * 0.84);
}
