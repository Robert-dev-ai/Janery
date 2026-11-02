import { inject, Injectable } from '@angular/core';

import { AuthService } from '../../core/auth/auth';
import { esCreible, FichaJuego } from './catalogo';

export interface Partida {
  puntos: number;
  fecha: string;
}

export interface Marca {
  mejor: number | null;
  fecha: string | null;
  partidas: Partida[];
}

export interface Resultado {
  puntos: number;
  nuevoRecord: boolean;
  mejor: number | null;
}

const PARTIDAS_GUARDADAS = 10;

/**
 * Récords de Janery Arcade de cada persona, en este navegador. No usan la base de datos a propósito: son un
 * entretenimiento y no deben añadir tablas ni tráfico al backend. Cada persona tiene su propia clave, por su id.
 */
@Injectable({ providedIn: 'root' })
export class MarcasArcade {
  private readonly auth = inject(AuthService);

  leer(juego: string): Marca {
    return this.todas()[juego] ?? { mejor: null, fecha: null, partidas: [] };
  }

  /** Anota la partida. Si la marca no es creíble no se guarda y no cuenta como récord. */
  anotar(ficha: FichaJuego, puntos: number, segundos: number): Resultado {
    const marca = this.leer(ficha.id);
    if (!esCreible(ficha, puntos, segundos)) {
      return { puntos, nuevoRecord: false, mejor: marca.mejor };
    }
    const fecha = new Date().toISOString();
    const mejora = marca.mejor === null || (ficha.menorEsMejor ? puntos < marca.mejor : puntos > marca.mejor);
    // Una partida sin puntos no es un récord, aunque sea la primera.
    const nuevoRecord = mejora && (puntos > 0 || !!ficha.menorEsMejor);
    const actualizada: Marca = {
      mejor: nuevoRecord ? puntos : marca.mejor,
      fecha: nuevoRecord ? fecha : marca.fecha,
      partidas: [{ puntos, fecha }, ...marca.partidas].slice(0, PARTIDAS_GUARDADAS),
    };
    this.guardar({ ...this.todas(), [ficha.id]: actualizada });
    return { puntos, nuevoRecord, mejor: actualizada.mejor };
  }

  private clave(): string | null {
    const id = this.auth.yo()?.id;
    return id ? `janery-arcade-${id}` : null;
  }

  private todas(): Record<string, Marca> {
    const clave = this.clave();
    try {
      const guardado = clave ? localStorage.getItem(clave) : null;
      return guardado ? (JSON.parse(guardado) as Record<string, Marca>) : {};
    } catch {
      // Sin almacenamiento o con datos dañados: se empieza de cero.
      return {};
    }
  }

  private guardar(marcas: Record<string, Marca>): void {
    const clave = this.clave();
    try {
      if (clave) {
        localStorage.setItem(clave, JSON.stringify(marcas));
      }
    } catch {
      // Modo privado o almacenamiento lleno: el récord vale solo para esta visita.
    }
  }
}
