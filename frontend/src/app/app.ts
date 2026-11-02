import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Dialogo } from './shared/components/dialogo';

@Component({
  selector: 'app-root',
  imports: [Dialogo, RouterOutlet],
  template: '<router-outlet /><app-dialogo />',
})
export class App {}
