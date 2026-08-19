import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Root component containing only the top-level router outlet. */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class AppComponent {}