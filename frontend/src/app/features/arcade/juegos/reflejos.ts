import { ACCION, azar, fondo, Juego, texto } from '../motor';

const RONDAS = 3;

/**
 * Reflejos: al ponerse verde hay que pulsar cuanto antes. Son tres rondas y el resultado es el promedio en
 * milisegundos: menos es mejor. Pulsar antes de tiempo repite la ronda.
 */
export class Reflejos extends Juego {
  private estado: 'espera' | 'ya' | 'pronto' | 'marca' = 'espera';
  private restante = azar(1.2, 3.2);
  private reloj = 0;
  private tiempos: number[] = [];
  private verdeDesde = 0;

  actualizar(dt: number): void {
    this.reloj += dt;
    if (this.estado === 'espera' && this.reloj >= this.restante) {
      this.estado = 'ya';
      this.reloj = 0;
      this.verdeDesde = performance.now();
    } else if ((this.estado === 'pronto' || this.estado === 'marca') && this.reloj >= 1) {
      this.siguienteRonda();
    }
  }

  private siguienteRonda(): void {
    if (this.tiempos.length >= RONDAS) {
      this.puntos = Math.round(this.tiempos.reduce((a, b) => a + b, 0) / this.tiempos.length);
      this.terminado = true;
      return;
    }
    this.estado = 'espera';
    this.restante = azar(1.2, 3.2);
    this.reloj = 0;
  }

  private pulsar(): void {
    if (this.estado === 'espera') {
      this.estado = 'pronto';
      this.reloj = 0;
    } else if (this.estado === 'ya') {
      // Con el reloj del navegador y no por cuadros, para no sumar hasta 16 ms de error.
      this.tiempos.push(Math.round(performance.now() - this.verdeDesde));
      this.estado = 'marca';
      this.reloj = 0;
    }
  }

  dibujar(c: CanvasRenderingContext2D): void {
    const centro = this.alto / 2;
    const ronda = `Ronda ${Math.min(this.tiempos.length + 1, RONDAS)}/${RONDAS}`;
    switch (this.estado) {
      case 'espera':
        fondo(c, this.ancho, this.alto, '#3a1d1d');
        texto(c, 'Espere el verde…', this.ancho / 2, centro, '#f5d0d0', 12);
        break;
      case 'ya':
        fondo(c, this.ancho, this.alto, '#15803d');
        texto(c, '¡Ya! Pulse', this.ancho / 2, centro, '#ffffff', 13);
        break;
      case 'pronto':
        fondo(c, this.ancho, this.alto, '#4a3412');
        texto(c, 'Muy pronto: se repite la ronda', this.ancho / 2, centro, '#fde6bf', 11);
        break;
      case 'marca':
        fondo(c, this.ancho, this.alto, '#12263a');
        texto(c, `${this.tiempos.at(-1)} ms`, this.ancho / 2, centro, '#dbeafe', 13);
        break;
    }
    if (this.ancho > 260) {
      texto(c, ronda, 8, centro, 'rgba(255,255,255,0.6)', 10, 'left');
    }
  }

  override tecla(codigo: string, abajo: boolean): boolean {
    if (!ACCION.includes(codigo)) {
      return false;
    }
    if (abajo) {
      this.pulsar();
    }
    return true;
  }

  override puntero(_x: number, _y: number, abajo: boolean): void {
    if (abajo) {
      this.pulsar();
    }
  }
}
