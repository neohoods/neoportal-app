import { Routes } from '@angular/router';
import { NotificationsSettingsComponent } from './notifications/notifications-settings.component';
import { ProfileComponent } from './profile/profile.component';
import { PropertiesComponent } from './properties/properties.component';
import { SettingsLandingPageComponent } from './settings-landing-page/settings-landing-page.component';

export const SETTINGS_ROUTES: Routes = [
  {
    path: '',
    component: SettingsLandingPageComponent,
    children: [
      { path: '', redirectTo: 'profile', pathMatch: 'full' }, // Redirect to 'profile'
      { path: 'profile', component: ProfileComponent },
      { path: 'properties', component: PropertiesComponent },
      { path: 'notifications', component: NotificationsSettingsComponent },

    ],
  },
];
