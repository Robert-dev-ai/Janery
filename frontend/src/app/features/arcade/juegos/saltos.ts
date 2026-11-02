import { ABAJO, ARRIBA, azar, circulo, fondo, Juego, limitar } from '../motor';

interface Plataforma {
  x: number;
  y: number;
  largo: number;
  /** Velocidad vertical de las que se mueven; cero en las fijas. */
  vy: number;
}

const GRAVEDAD = 330;
const REBOTE = 195;
const GROSOR = 3;

/**
 * Saltos, en horizontal: la gravedad tira hacia la izquierda y la moneda rebota en cada plataforma que toca al caer.
 * Solo se elige la altura; quien se queda atrás del borde izquierdo pierde. Los puntos son la distancia avanzada.
 */
export class Saltos extends Juego {
  private x = 30;
  private y = this.alto / 2;
  private vx = REBOTE;
  private camara = 0;
  private plataformas: Plataforma[] = [];
  private subir = false;
  private bajar = false;
  private objetivo: number | null = null;

  constructor(ancho: number, alto: number) {
    super(ancho, alto);
    this.plataformas.push({ x: 16, y: 0, largo: alto, vy: 0 });
    this.completar();
  }

  private completar(): void {
    let ultima = this.plataformas.at(-1)!;
    while (ultima.x < this.camara + this.ancho + 60) {
      const dificultad = Math.min(1, this.camara / 3000);
      const largo = this.alto * (0.45 - dificultad * 0.15);
      ultima = {
        x: ultima.x + azar(22, 34 + dificultad * 16),
        y: azar(1, this.alto - largo - 1),
        largo,
        vy: Math.random() < dificultad * 0.5 ? azar(-1, 1) * this.alto * 0.8 : 0,
      };
      this.plataformas.push(ultima);
    }
  }

  actualizar(dt: number): void {
    const rapidez = this.alto * 2.4;
    if (this.objetivo !== null) {
      this.y += limitar(this.objetivo - this.y, -rapidez * dt * 1.5, rapidez * dt * 1.5);
    }
    this.y = limitar(this.y + ((this.bajar ? 1 : 0) - (this.subir ? 1 : 0)) * rapidez * dt, 3, this.alto - 3);

    for (const p of this.plataformas) {
      if (p.vy) {
        p.y += p.vy * dt;
        if (p.y < 0 || p.y + p.largo > this.alto) {
          p.vy = -p.vy;
          p.y = limitar(p.y, 0, this.alto - p.largo);
        }
      }
    }

    const antes = this.x;
    this.vx -= GRAVEDAD * dt;
    this.x += this.vx * dt;
    // Solo rebota al caer sobre la cara derecha: al subir atraviesa las plataformas.
    if (this.vx < 0) {
      const apoyo = this.plataformas.find((p) => antes - 3 >= p.x + GROSOR && this.x - 3 <= p.x + GROSOR && this.y + 3 >= p.y && this.y - 3 <= p.y + p.largo);
      if (apoyo) {
        this.x = apoyo.x + GROSOR + 3;
        this.vx = REBOTE;
      }
    }

    // La vista avanza despacio por sí sola: quedarse rebotando en la misma plataforma no salva la partida.
    const margen = limitar(this.ancho * 0.4, 62, 110);
    this.camara = Math.max(this.camara + (10 + this.camara / 200) * dt, this.x - margen);
    this.puntos = Math.floor(this.camara / 10);
    this.plataformas = this.plataformas.filter((p) => p.x > this.camara - 10);
    this.completar();
    this.terminado = this.x < this.camara - 4;
  }

  dibujar(c: CanvasRenderingContext2D): void {
    fondo(c, this.ancho, this.alto, '#13201c');
    c.save();
    c.translate(-this.camara, 0);
    for (const p of this.plataformas) {
      c.fillStyle = p.vy ? '#5fa8d3' : '#4fa868';
      c.fillRect(p.x, p.y, GROSOR, p.largo);
    }
    circulo(c, this.x, this.y, 3.5, '#e9b949');
    circulo(c, this.x, this.y, 1.8, '#f6d77a');
    c.restore();
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
