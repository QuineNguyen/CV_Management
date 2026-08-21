import { RenderMode, ServerRoute } from '@angular/ssr';
import { AppRoute } from './core/enums/app-route.enum';

export const serverRoutes: ServerRoute[] = [
  {
    // The only route that doesn't depend on auth state read from localStorage,
    // so it's the only one that can be rendered once at build time.
    path: AppRoute.Login,
    renderMode: RenderMode.Prerender
  },
  {
    // Every other route sits behind authGuard/mustChangePasswordGuard, which read
    // the session from localStorage — unavailable during server rendering. Prerendering
    // them would bake in a permanent redirect to /login. Render them client-side instead.
    path: '**',
    renderMode: RenderMode.Client
  }
];
