import { Directive, ElementRef, inject, OnDestroy, OnInit, Renderer2 } from '@angular/core';

const OJO = '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg>';
const OJO_TACHADO = '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 19c-6.5 0-10-7-10-7a18.5 18.5 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c6.5 0 10 7 10 7a18.5 18.5 0 0 1-2.16 3.19"/><path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/><path d="m2 2 20 20"/></svg>';

/** Añade a un campo de contraseña el botón para mostrarla u ocultarla. Uso: <input type="password" appVerClave />. */
@Directive({ selector: 'input[appVerClave]' })
export class VerClave implements OnInit, OnDestroy {
  private readonly campo = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;
  private readonly r = inject(Renderer2);
  private boton?: HTMLButtonElement;
  private quitar?: () => void;

  ngOnInit(): void {
    const envoltura = this.r.createElement('span') as HTMLSpanElement;
    this.r.addClass(envoltura, 'campo-clave');
    const padre = this.campo.parentNode!;
    this.r.insertBefore(padre, envoltura, this.campo);
    this.r.appendChild(envoltura, this.campo);

    const boton = this.r.createElement('button') as HTMLButtonElement;
    boton.type = 'button';
    this.r.addClass(boton, 'ver-clave');
    this.actualizar(boton, false);
    this.r.appendChild(envoltura, boton);
    this.boton = boton;
    this.quitar = this.r.listen(boton, 'click', () => {
      const visible = this.campo.type === 'password';
      this.campo.type = visible ? 'text' : 'password';
      this.actualizar(boton, visible);
    });
  }

  ngOnDestroy(): void {
    this.quitar?.();
    this.boton?.remove();
  }

  private actualizar(boton: HTMLButtonElement, visible: boolean): void {
    boton.innerHTML = visible ? OJO_TACHADO : OJO;
    const texto = visible ? 'Ocultar contraseña' : 'Mostrar contraseña';
    boton.setAttribute('aria-label', texto);
    boton.title = texto;
    boton.setAttribute('aria-pressed', String(visible));
  }
}
