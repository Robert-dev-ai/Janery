import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface EstadoApi {
  servicio: string;
  estado: string;
}

@Injectable({ providedIn: 'root' })
export class EstadoApiService {
  private readonly http = inject(HttpClient);

  consultar(): Observable<EstadoApi> {
    return this.http.get<EstadoApi>(`${environment.apiBaseUrl}/public/estado`);
  }
}
