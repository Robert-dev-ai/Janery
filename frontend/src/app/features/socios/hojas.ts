/** Apoyo de las hojas de cartera y de ahorros del área de socios. */

export type Grupo = 'TODOS' | 'SOCIOS' | 'CLIENTES';

const MODALIDADES: Record<string, string> = {
  NORMAL: 'Normal',
  INVERSION: 'Inversión',
  ESTUDIO: 'Estudio',
  PERSONALIZADO: 'Personalizado',
  ESTUDIANTIL: 'Estudiantil',
  NAVIDENO: 'Navideño',
};

export function nombreModalidad(codigo: string | null): string {
  return codigo ? (MODALIDADES[codigo] ?? codigo) : '';
}

/** Sin distinguir mayúsculas ni tildes, y con el nombre en cualquier orden. */
function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}

export function coincide(p: { nombre: string; socio: boolean }, grupo: Grupo, buscar: string): boolean {
  if ((grupo === 'SOCIOS' && !p.socio) || (grupo === 'CLIENTES' && p.socio)) {
    return false;
  }
  const nombre = normalizar(p.nombre);
  return normalizar(buscar).split(/\s+/).every((parte) => nombre.includes(parte));
}

/** Suma en centavos para que los decimales no dejen restos como 0,30000000000000004. */
export function sumar(importes: (number | null | undefined)[]): number {
  return importes.reduce<number>((total, v) => total + Math.round((v ?? 0) * 100), 0) / 100;
}
