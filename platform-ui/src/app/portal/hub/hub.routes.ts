import { Routes } from '@angular/router';
import { getGlobalProviders } from '../../global.provider';
import HubLayoutComponent from './hub-layout/hub-layout.component';
import { hubProviders } from './hub.provider';


export const COMMUNITY_ROUTES: Routes = [
  {
    path: '',
    component: HubLayoutComponent,
    providers: [...hubProviders, ...getGlobalProviders()],
    children: [
      { path: '', redirectTo: 'items', pathMatch: 'full' },
      {
        path: 'settings',
        loadChildren: () =>
          import('./pages/settings/settings.routes').then(
            (m) => m.SETTINGS_ROUTES,
          ),
      },
    ],
  },
];
