import { Routes } from '@angular/router';
import { getGlobalProviders } from '../../global.provider';
import { AdminLayoutComponent } from './admin-layout/admin-layout.component';
import { adminProviders } from './admin.providers';

import { EditUserComponent } from './pages/edit-user/edit-user.component';

import { SecurityComponent } from './pages/security/security.component';
import { UsersComponent } from './pages/users/users.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminLayoutComponent,
    providers: [...adminProviders, ...getGlobalProviders()],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }, // Redirect to 'profile'
      { path: 'users', component: UsersComponent },
      { path: 'users/add', component: EditUserComponent },
      { path: 'users/:id/edit', component: EditUserComponent },
      { path: 'security', component: SecurityComponent }
    ],
  },
];
