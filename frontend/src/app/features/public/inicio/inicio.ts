import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HandCoins, LucideAngularModule, PiggyBank, Wallet } from 'lucide-angular';

import { EstadoApiService } from '../../../core/services/estado-api';

type Conexion = 'comprobando' | 'disponible' | 'no-disponible';

@Component({
  selector: 'app-inicio',
  imports: [LucideAngularModule, RouterLink],
  templateUrl: './inicio.html',
})
export class Inicio implements OnInit {
  private readonly estadoApi = inject(EstadoApiService);

  protected readonly iconos = { HandCoins, PiggyBank, Wallet };

  protected readonly conexion = signal<Conexion>('comprobando');

  ngOnInit(): void {
    this.estadoApi.consultar().subscribe({
      next: () => this.conexion.set('disponible'),
      error: () => this.conexion.set('no-disponible'),
    });
  }
}
