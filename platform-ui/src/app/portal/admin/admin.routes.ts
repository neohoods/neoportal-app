import { Routes } from '@angular/router';
import { getGlobalProviders } from '../../global.provider';
import { AdminGuard } from '../../guards/admin-guards';
import { AdminLayoutComponent } from './admin-layout/admin-layout.component';
import { adminProviders } from './admin.providers';

import { EditUserComponent } from './pages/edit-user/edit-user.component';

import { ApplicationsEditComponent } from './pages/applications-edit/applications-edit.component';
import { ApplicationsComponent } from './pages/applications/applications.component';
import { CustomPagesEditComponent } from './pages/custom-pages-edit/custom-pages-edit.component';
import { CustomPagesComponent } from './pages/custom-pages/custom-pages.component';
import { EmailTemplatesEditComponent } from './pages/email-templates-edit/email-templates-edit.component';
import { EmailTemplatesComponent } from './pages/email-templates/email-templates.component';
import { HelpCenterEditComponent } from './pages/help-center/help-center-edit.component';
import { InfosComponent } from './pages/infos/infos.component';
import { NewsletterLogsComponent } from './pages/newsletter-logs/newsletter-logs.component';
import { NewslettersEditComponent } from './pages/newsletters-edit/newsletters-edit.component';
import { NewslettersComponent } from './pages/newsletters/newsletters.component';
import { ReservationDetailComponent } from './pages/reservation-detail/reservation-detail.component';
import { ReservationsAdminComponent } from './pages/reservations/reservations-admin.component';
import { SecurityComponent } from './pages/security/security.component';
import { SpaceSettingsComponent } from './pages/space-settings/space-settings.component';
import { SpacesAdminComponent } from './pages/spaces/spaces-admin.component';
import { JoinRequestsAdminComponent } from './pages/units/join-requests-admin.component';
import { UnitEditAdminComponent } from './pages/units/unit-edit/unit-edit-admin.component';
import { UnitsAdminComponent } from './pages/units/units-admin.component';
import { UsersComponent } from './pages/users/users.component';
import { MatrixBotComponent } from './pages/matrix-bot/matrix-bot.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminLayoutComponent,
    canActivate: [AdminGuard],
    providers: [...adminProviders, ...getGlobalProviders()],
    children: [
      { path: '', redirectTo: 'infos', pathMatch: 'full' }, // Redirect to 'profile'
      { path: 'users', component: UsersComponent },
      { path: 'users/add', component: EditUserComponent },
      { path: 'users/:id/edit', component: EditUserComponent },
      { path: 'units', component: UnitsAdminComponent },
      { path: 'units/add', component: UnitEditAdminComponent },
      { path: 'units/join-requests', component: JoinRequestsAdminComponent },
      { path: 'units/:id', loadComponent: () => import('./pages/units/unit-detail/unit-detail-admin.component').then(m => m.UnitDetailAdminComponent) },
      { path: 'units/:id/edit', component: UnitEditAdminComponent },
      { path: 'security', component: SecurityComponent },
      { path: 'settings/spaces', component: SpaceSettingsComponent },
      { path: 'pages', component: CustomPagesComponent },
      { path: 'pages/add', component: CustomPagesEditComponent },
      { path: 'pages/help-center/edit', component: HelpCenterEditComponent },
      { path: 'pages/:id/edit', component: CustomPagesEditComponent },
      { path: 'applications', component: ApplicationsComponent },
      { path: 'applications/add', component: ApplicationsEditComponent },
      { path: 'applications/:id/edit', component: ApplicationsEditComponent },
      { path: 'infos', component: InfosComponent },
      { path: 'newsletters', component: NewslettersComponent },
      { path: 'newsletters/create', component: NewslettersEditComponent },
      { path: 'newsletters/:id/edit', component: NewslettersEditComponent },
      { path: 'newsletters/:newsletterId/logs', component: NewsletterLogsComponent },
      { path: 'email-templates', component: EmailTemplatesComponent },
      { path: 'email-templates/create', component: EmailTemplatesEditComponent },
      { path: 'email-templates/:id/edit', component: EmailTemplatesEditComponent },
      { path: 'spaces', component: SpacesAdminComponent },
      { path: 'spaces/add', loadComponent: () => import('./pages/spaces-edit/spaces-edit.component').then((m) => m.SpacesEditComponent) },
      { path: 'spaces/:id', loadComponent: () => import('./pages/spaces-view/spaces-view.component').then((m) => m.SpacesViewComponent) },
      { path: 'spaces/:id/edit', loadComponent: () => import('./pages/spaces-edit/spaces-edit.component').then((m) => m.SpacesEditComponent) },
      { path: 'reservations', component: ReservationsAdminComponent },
      { path: 'reservations/:id', component: ReservationDetailComponent },
      // Digital Locks routes
      { path: 'digital-locks', loadComponent: () => import('./pages/digital-lock/digital-lock-admin.component').then((m) => m.DigitalLockAdminComponent) },
      { path: 'digital-locks/add', loadComponent: () => import('./pages/digital-lock/digital-lock-edit.component').then((m) => m.DigitalLockEditComponent) },
      { path: 'digital-locks/:id/edit', loadComponent: () => import('./pages/digital-lock/digital-lock-edit.component').then((m) => m.DigitalLockEditComponent) },
      { path: 'matrix-bot', component: MatrixBotComponent },
    ],
  },
];
