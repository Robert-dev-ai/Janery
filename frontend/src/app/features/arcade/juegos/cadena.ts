import { ABAJO, ARRIBA, DERECHA, fondo, IZQUIERDA, Juego, moneda } from '../motor';

interface Celda {
  x: number;
  y: number;
}

/** Cadena: la fila de monedas crece con cada moneda que recoge; no puede chocar con el borde ni consigo misma. */
export class Cadena extends Juego {
  private readonly lado: number;
  private readonly columnas: number;
  private readonly filas: number;
  private cuerpo: Celda[];
  private direccion: Celda = { x: 1, y: 0 };
  private pendientes: Celda[] = [];
  private comida: Celda;
  private espera = 0;
  private intervalo = 0.13;

  constructor(ancho: number, alto: number) {
    super(ancho, alto);
    this.lado = Math.max(5, Math.floor(alto / 6));
    this.columnas = Math.floor(ancho / this.lado);
    this.filas = Math.floor(alto / this.lado);
    const fila = Math.floor(this.filas / 2);
    this.cuerpo = [{ x: 3, y: fila }, { x: 2, y: fila }, { x: 1, y: fila }];
    this.comida = this.lugarLibre();
  }

  private get origenY(): number {
    return (this.alto - this.filas * this.lado) / 2;
  }

  actualizar(dt: number): void {
    this.espera += dt;
    while (this.espera >= this.intervalo && !this.terminado) {
      this.espera -= this.intervalo;
      this.paso();
    }
  }

  private paso(): void {
    const siguiente = this.pendientes.shift();
    if (siguiente) {
      this.direccion = siguiente;
    }
    const cabeza = { x: this.cuerpo[0].x + this.direccion.x, y: this.cuerpo[0].y + this.direccion.y };
    const fuera = cabeza.x < 0 || cabeza.y < 0 || cabeza.x >= this.columnas || cabeza.y >= this.filas;
    const come = cabeza.x === this.comida.x && cabeza.y === this.comida.y;
    // La cola se mueve en este paso, salvo que crezca: por eso no cuenta como choque.
    const cuerpo = come ? this.cuerpo : this.cuerpo.slice(0, -1);
    if (fuera || cuerpo.some((c) => c.x === cabeza.x && c.y === cabeza.y)) {
      this.terminado = true;
      return;
    }
    this.cuerpo = [cabeza, ...cuerpo];
    if (come) {
      this.puntos++;
      this.intervalo = Math.max(0.065, this.intervalo * 0.97);
      if (this.cuerpo.length >= this.columnas * this.filas) {
        this.terminado = true;
        return;
      }
      this.comida = this.lugarLibre();
    }
  }

  private lugarLibre(): Celda {
    const libres: Celda[] = [];
    for (let x = 0; x < this.columnas; x++) {
      for (let y = 0; y < this.filas; y++) {
        if (!this.cuerpo.some((c) => c.x === x && c.y === y)) {
          libres.push({ x, y });
        }
      }
    }
    return libres[Math.floor(Math.random() * libres.length)];
  }

  dibujar(c: CanvasRenderingContext2D): void {
    fondo(c, this.ancho, this.alto, '#121c19');
    const l = this.lado;
    const oy = this.origenY;
    c.strokeStyle = '#2a3b35';
    c.strokeRect(0.5, oy + 0.5, this.columnas * l - 1, this.filas * l - 1);
    moneda(c, this.comida.x * l + l / 2, oy + this.comida.y * l + l / 2, l / 2 - 0.5);
    this.cuerpo.forEach((celda, i) => {
      c.fillStyle = i === 0 ? '#6ee7a0' : i % 2 ? '#2fa362' : '#279156';
      c.fillRect(celda.x * l + 0.5, oy + celda.y * l + 0.5, l - 1, l - 1);
    });
  }

  override tecla(codigo: string, abajo: boolean): boolean {
    const direccion = ARRIBA.includes(codigo) ? { x: 0, y: -1 }
      : ABAJO.includes(codigo) ? { x: 0, y: 1 }
      : IZQUIERDA.includes(codigo) ? { x: -1, y: 0 }
      : DERECHA.includes(codigo) ? { x: 1, y: 0 }
      : null;
    if (!direccion) {
      return false;
    }
    if (abajo) {
      this.girar(direccion);
    }
    return true;
  }

  /** En pantalla táctil se toca hacia donde debe girar la cabeza: arriba o abajo de ella, o a un lado. */
  override puntero(x: number, y: number, abajo: boolean): void {
    if (!abajo) {
      return;
    }
    const actual = this.pendientes.at(-1) ?? this.direccion;
    const cabeza = this.cuerpo[0];
    if (actual.x !== 0) {
      this.girar({ x: 0, y: y < this.origenY + (cabeza.y + 0.5) * this.lado ? -1 : 1 });
    } else {
      this.girar({ x: x < (cabeza.x + 0.5) * this.lado ? -1 : 1, y: 0 });
    }
  }

  private girar(nueva: Celda): void {
    const actual = this.pendientes.at(-1) ?? this.direccion;
    if ((nueva.x !== 0 && actual.x !== 0) || (nueva.y !== 0 && actual.y !== 0) || this.pendientes.length >= 2) {
      return;
    }
    this.pendientes.push(nueva);
  }
}
