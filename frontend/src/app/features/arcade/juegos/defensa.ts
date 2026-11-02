import { ABAJO, ACCION, ARRIBA, Caja, choca, fondo, Juego, limitar } from '../motor';

interface Enemigo extends Caja {
  vivo: boolean;
}

/**
 * Defensa: la nave, a la izquierda, sube, baja y dispara contra la formación de gastos que avanza desde la derecha.
 * Cada gasto derribado suma diez; si la formación llega a la nave, se acaba. En pantalla táctil dispara sola.
 */
export class Defensa extends Juego {
  private nave = this.alto / 2;
  private enemigos: Enemigo[] = [];
  private disparos: Caja[] = [];
  private balasEnemigas: Caja[] = [];
  private oleada = 0;
  private recarga = 0;
  private fase = 0;
  private vidas = 3;
  private invulnerable = 0;
  private subir = false;
  private bajar = false;
  private disparando = false;
  private objetivo: number | null = null;

  constructor(ancho: number, alto: number) {
    super(ancho, alto);
    this.nuevaOleada();
  }

  private nuevaOleada(): void {
    const filas = 3;
    const columnas = Math.max(3, Math.min(9, Math.floor(this.ancho / 55)));
    const separacion = (this.alto - 4) / filas;
    this.enemigos = [];
    for (let col = 0; col < columnas; col++) {
      for (let fila = 0; fila < filas; fila++) {
        this.enemigos.push({ x: this.ancho - 10 - col * 14, y: 2 + fila * separacion + (separacion - 7) / 2, w: 8, h: 7, vivo: true });
      }
    }
    this.oleada++;
  }

  actualizar(dt: number): void {
    const rapidez = this.alto * 4;
    if (this.objetivo !== null) {
      this.nave += limitar(this.objetivo - this.nave, -rapidez * dt * 1.5, rapidez * dt * 1.5);
    }
    this.nave = limitar(this.nave + ((this.bajar ? 1 : 0) - (this.subir ? 1 : 0)) * rapidez * dt, 4, this.alto - 4);

    this.recarga -= dt;
    if (this.disparando && this.recarga <= 0) {
      this.disparos.push({ x: 14, y: this.nave - 1, w: 5, h: 2 });
      this.recarga = 0.32;
    }
    for (const d of this.disparos) {
      d.x += 300 * dt;
    }
    this.disparos = this.disparos.filter((d) => d.x < this.ancho);

    // La formación avanza y se mece arriba y abajo; cuantos menos quedan, más rápido van.
    const vivos = this.enemigos.filter((e) => e.vivo);
    const avance = (6 + this.oleada * 2.5 + (1 - vivos.length / this.enemigos.length) * 10) * dt;
    this.fase += dt * 2;
    const meneo = Math.cos(this.fase) * 0.6;
    for (const e of vivos) {
      e.x -= avance;
      e.y = limitar(e.y + meneo * dt * 4, 0, this.alto - e.h);
    }

    for (const d of this.disparos) {
      const blanco = vivos.find((e) => e.vivo && choca(d, e));
      if (blanco) {
        blanco.vivo = false;
        d.x = this.ancho;
        this.puntos += 10;
      }
    }

    if (vivos.length && Math.random() < dt * (0.6 + this.oleada * 0.2)) {
      const tirador = vivos[Math.floor(Math.random() * vivos.length)];
      this.balasEnemigas.push({ x: tirador.x - 3, y: tirador.y + tirador.h / 2 - 1, w: 4, h: 2 });
    }
    for (const b of this.balasEnemigas) {
      b.x -= (90 + this.oleada * 10) * dt;
    }
    this.balasEnemigas = this.balasEnemigas.filter((b) => b.x > -4);

    this.invulnerable -= dt;
    const casco = { x: 4, y: this.nave - 4, w: 9, h: 8 };
    const impacto = this.balasEnemigas.findIndex((b) => choca(b, casco));
    if (impacto >= 0 && this.invulnerable <= 0) {
      this.balasEnemigas.splice(impacto, 1);
      this.vidas--;
      this.invulnerable = 1;
    }

    if (this.vidas <= 0 || vivos.some((e) => e.x < 16)) {
      this.terminado = true;
    } else if (!this.enemigos.some((e) => e.vivo)) {
      this.balasEnemigas = [];
      this.nuevaOleada();
    }
  }

  dibujar(c: CanvasRenderingContext2D): void {
    fondo(c, this.ancho, this.alto, '#0e1422');
    c.fillStyle = '#28324a';
    for (let i = 0; i < 14; i++) {
      c.fillRect((i * 97 + this.fase * 8) % this.ancho, (i * 13) % this.alto, 1, 1);
    }
    for (const e of this.enemigos) {
      if (!e.vivo) {
        continue;
      }
      c.fillStyle = '#d9604c';
      c.fillRect(e.x + 1, e.y, e.w - 2, e.h);
      c.fillRect(e.x, e.y + 2, e.w, 3);
      c.fillStyle = '#0e1422';
      c.fillRect(e.x + 2, e.y + 2, 1, 1);
      c.fillRect(e.x + 2, e.y + 4, 1, 1);
    }
    c.fillStyle = '#f6d77a';
    for (const d of this.disparos) {
      c.fillRect(d.x, d.y, d.w, d.h);
    }
    c.fillStyle = '#f58a8a';
    for (const b of this.balasEnemigas) {
      c.fillRect(b.x, b.y, b.w, b.h);
    }
    if (this.invulnerable <= 0 || Math.floor(this.invulnerable * 10) % 2 === 0) {
      c.fillStyle = '#4fd88a';
      c.beginPath();
      c.moveTo(4, this.nave - 4);
      c.lineTo(14, this.nave);
      c.lineTo(4, this.nave + 4);
      c.closePath();
      c.fill();
    }
    for (let i = 0; i < this.vidas; i++) {
      c.fillStyle = '#4fd88a';
      c.fillRect(18 + i * 5, this.alto - 3, 3, 2);
    }
  }

  override tecla(codigo: string, abajo: boolean): boolean {
    if (ARRIBA.includes(codigo)) {
      this.subir = abajo;
      this.objetivo = null;
    } else if (ABAJO.includes(codigo)) {
      this.bajar = abajo;
      this.objetivo = null;
    } else if (ACCION.includes(codigo)) {
      this.disparando = abajo;
    } else {
      return false;
    }
    return true;
  }

  /** Con el dedo o el ratón: la nave sigue al puntero y dispara mientras se mantiene pulsado. */
  override puntero(_x: number, y: number, abajo: boolean): void {
    this.disparando = abajo;
    if (abajo) {
      this.objetivo = y;
    }
  }

  override moverPuntero(_x: number, y: number): void {
    if (this.disparando) {
      this.objetivo = y;
    }
  }
}
