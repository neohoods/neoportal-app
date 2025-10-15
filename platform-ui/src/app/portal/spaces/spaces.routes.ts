import { Routes } from '@angular/router';
import { getGlobalProviders } from '../../global.provider';
import { SpacesLayoutComponent } from './layout/spaces-layout.component';
import { spacesProviders } from './spaces.provider';

export const SPACES_ROUTES: Routes = [
    {
        path: '',
        component: SpacesLayoutComponent,
        providers: [...spacesProviders, ...getGlobalProviders()],
        children: [
            { path: '', redirectTo: 'list', pathMatch: 'full' },
            {
                path: 'list',
                loadComponent: () =>
                    import('./pages/spaces-list/spaces-list.component').then(
                        (m) => m.SpacesListComponent,
                    ),
            },
            {
                path: 'detail/:id',
                loadComponent: () =>
                    import('./pages/space-detail/space-detail.component').then(
                        (m) => m.SpaceDetailComponent,
                    ),
            },
            {
                path: 'reservations',
                loadComponent: () =>
                    import('./pages/my-reservations/my-reservations.component').then(
                        (m) => m.MyReservationsComponent,
                    ),
            },
            {
                path: 'reservations/success',
                loadComponent: () =>
                    import('./pages/reservation-success/reservation-success.component').then(
                        (m) => m.ReservationSuccessComponent,
                    ),
            },
            {
                path: 'reservations/cancel',
                loadComponent: () =>
                    import('./pages/reservation-cancel/reservation-cancel.component').then(
                        (m) => m.ReservationCancelComponent,
                    ),
            },
            {
                path: 'reservations/:id',
                loadComponent: () =>
                    import('./pages/reservation-detail/reservation-detail.component').then(
                        (m) => m.ReservationDetailComponent,
                    ),
            },
            {
                path: 'favorites',
                loadComponent: () =>
                    import('./pages/favorites/favorites.component').then(
                        (m) => m.FavoritesComponent,
                    ),
            }
        ],
    },
];
