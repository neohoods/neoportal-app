import { Routes } from '@angular/router';
import { ProfileCompletionGuard } from '../../guards/profile-completion.guard';
import { getGlobalProviders } from '../../global.provider';
import HubLayoutComponent from './hub-layout/hub-layout.component';
import { hubProviders } from './hub.provider';
import { WallComponent } from './pages/wall/wall.component';
import { CustomPageComponent } from './pages/custom-page/custom-page.component';
import { HelpCenterComponent } from './pages/help-center/help-center.component';


export const COMMUNITY_ROUTES: Routes = [
  {
    path: '',
    component: HubLayoutComponent,
    providers: [...hubProviders, ...getGlobalProviders()],
    children: [
      { path: '', redirectTo: 'wall', pathMatch: 'full' },
      { path: 'help-center', component: HelpCenterComponent, canActivate: [ProfileCompletionGuard] },
      { path: 'page/:ref', component: CustomPageComponent, canActivate: [ProfileCompletionGuard] },
      {
        path: 'wall',
        component: WallComponent,
        canActivate: [ProfileCompletionGuard],
      },
      {
        path: 'settings',
        loadChildren: () =>
          import('./pages/settings/settings.routes').then(
            (m) => m.SETTINGS_ROUTES,
          ),
      },
      {
        path: 'directory',
        loadComponent: () =>
          import('./pages/directory/directory.component').then(
            (m) => m.DirectoryComponent,
          ),
        canActivate: [ProfileCompletionGuard],
      },
    ],
  },
];
