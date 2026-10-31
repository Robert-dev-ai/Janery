import { Component } from '@angular/core';

@Component({
  selector: 'app-informacion',
  template: `
    <h1>Información sobre Janery</h1>
    <p>Janery es una caja de ahorro y crédito. Registrarse es gratuito y le permite consultar esta información sin obligación de operar.</p>

    <section class="tarjeta">
      <h2>Cuenta principal</h2>
      <p>Es su cuenta central. Recibe desembolsos y fondos liberados, y permite mantener dinero disponible.
        Se abre con un pago inicial, parte del cual es un depósito que se le devuelve al cerrar la cuenta.</p>
    </section>
    <section class="tarjeta">
      <h2>Créditos</h2>
      <p>Créditos a letras (normal, inversión, estudio y personalizado) y créditos emergentes.</p>
    </section>
    <section class="tarjeta">
      <h2>Ahorros</h2>
      <p>Ahorro normal, con retiro cuando lo desee, y ahorro a plazo fijo.</p>
    </section>
  `,
})
export class Informacion {}
