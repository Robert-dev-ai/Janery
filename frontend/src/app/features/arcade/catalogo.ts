import { ClaseJuego } from './motor';

export interface FichaJuego {
  id: string;
  nombre: string;
  invitacion: string;
  /** Texto para lectores de pantalla y la ayuda del lienzo. */
  controles: string;
  /** En Reflejos se mide el tiempo: gana el número más bajo. */
  menorEsMejor?: boolean;
  unidad?: string;
  /** Si es false, el propio juego muestra su marcador. */
  marcador?: boolean;
  /** Tope de puntos creíble por segundo de juego: lo que lo supere se descarta como manipulado. */
  maximoPorSegundo: number;
  /** Cada juego vive en su propio archivo y solo se descarga al jugarlo. */
  cargar: () => Promise<ClaseJuego>;
}

export const JUEGOS: FichaJuego[] = [
  {
    id: 'corredor', nombre: 'Carrera', invitacion: 'Salte los obstáculos del camino',
    controles: 'Espacio, flecha arriba o toque para saltar', maximoPorSegundo: 45,
    cargar: () => import('./juegos/corredor').then((m) => m.Corredor),
  },
  {
    id: 'vuelo', nombre: 'Vuelo', invitacion: 'Mantenga la alcancía en el aire',
    controles: 'Espacio, flecha arriba o toque para impulsar', maximoPorSegundo: 3,
    cargar: () => import('./juegos/vuelo').then((m) => m.Vuelo),
  },
  {
    id: 'cadena', nombre: 'Cadena', invitacion: 'Recoja monedas sin chocar',
    controles: 'Flechas o WASD; en pantalla táctil, toque hacia donde girar', maximoPorSegundo: 16,
    cargar: () => import('./juegos/cadena').then((m) => m.Cadena),
  },
  {
    id: 'rebote', nombre: 'Rebote', invitacion: 'Devuelva la pelota al rival',
    controles: 'Flechas arriba y abajo, o deslice el dedo', maximoPorSegundo: 10,
    cargar: () => import('./juegos/rebote').then((m) => m.Rebote),
  },
  {
    id: 'ladrillos', nombre: 'Ladrillos', invitacion: 'Derribe el muro con la pelota',
    controles: 'Flechas arriba y abajo, o deslice el dedo', maximoPorSegundo: 20,
    cargar: () => import('./juegos/ladrillos').then((m) => m.Ladrillos),
  },
  {
    id: 'defensa', nombre: 'Defensa', invitacion: 'Frene los gastos que avanzan',
    controles: 'Flechas para moverse y espacio para disparar; en pantalla táctil, mantenga pulsado', maximoPorSegundo: 35,
    cargar: () => import('./juegos/defensa').then((m) => m.Defensa),
  },
  {
    id: 'fusion', nombre: 'Fusión', invitacion: 'Sume fichas iguales',
    controles: 'Flechas izquierda y derecha, o deslice', maximoPorSegundo: 400,
    cargar: () => import('./juegos/fusion').then((m) => m.Fusion),
  },
  {
    id: 'tunel', nombre: 'Túnel', invitacion: 'Vuele sin tocar las paredes',
    controles: 'Mantenga espacio o el dedo para subir; suelte para bajar', maximoPorSegundo: 30,
    cargar: () => import('./juegos/tunel').then((m) => m.Tunel),
  },
  {
    id: 'reflejos', nombre: 'Reflejos', invitacion: 'Pulse en cuanto se ponga verde',
    controles: 'Espacio, Enter o toque', menorEsMejor: true, unidad: 'ms', marcador: false, maximoPorSegundo: 0,
    cargar: () => import('./juegos/reflejos').then((m) => m.Reflejos),
  },
  {
    id: 'saltos', nombre: 'Saltos', invitacion: 'Rebote de plataforma en plataforma',
    controles: 'Flechas arriba y abajo, o deslice el dedo', maximoPorSegundo: 25,
    cargar: () => import('./juegos/saltos').then((m) => m.Saltos),
  },
];

/** Un juego al azar distinto del anterior, si hay más de uno. */
export function elegirJuego(juegos: FichaJuego[], anterior: string | null, azar: () => number = Math.random): FichaJuego {
  const opciones = juegos.length > 1 ? juegos.filter((j) => j.id !== anterior) : juegos;
  return opciones[Math.floor(azar() * opciones.length)] ?? juegos[0];
}

/**
 * Descarta marcas imposibles: fracciones, negativos o más puntos de los que permite el tiempo jugado. En Reflejos,
 * nadie reacciona por debajo de 100 ms.
 */
export function esCreible(ficha: FichaJuego, puntos: number, segundos: number): boolean {
  if (!Number.isInteger(puntos) || puntos < 0 || !Number.isFinite(segundos) || segundos <= 0) {
    return false;
  }
  if (ficha.menorEsMejor) {
    return puntos >= 100 && puntos <= 10_000;
  }
  return puntos <= 10 + ficha.maximoPorSegundo * segundos;
}
