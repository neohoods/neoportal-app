import { Routes } from '@angular/router';
import { NotificationsSettingsComponent } from './notifications/notifications-settings.component';
import { ProfileComponent } from './profile/profile.component';
import { SettingsLandingPageComponent } from './settings-landing-page/settings-landing-page.component';
import { UnitsComponent } from './units/units.component';

export const SETTINGS_ROUTES: Routes = [
  {
    path: '',
    component: SettingsLandingPageComponent,
    children: [
      { path: '', redirectTo: 'profile', pathMatch: 'full' }, // Redirect to 'profile'
      { path: 'profile', component: ProfileComponent },
      { path: 'notifications', component: NotificationsSettingsComponent },
      { path: 'units', component: UnitsComponent },
      { path: 'units/:id', loadComponent: () => import('./units/unit-detail/unit-detail.component').then(m => m.UnitDetailComponent) },
      { path: 'units/:id/reservations', loadComponent: () => import('./units/unit-detail/unit-reservations.component').then(m => m.UnitReservationsComponent) },
    ],
  },
];
