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
import { SecurityComponent } from './pages/security/security.component';
import { UsersComponent } from './pages/users/users.component';

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
      { path: 'security', component: SecurityComponent },
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
    ],
  },
];
