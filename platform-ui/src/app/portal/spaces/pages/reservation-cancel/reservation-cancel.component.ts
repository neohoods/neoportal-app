import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiLoader } from '@taiga-ui/core';

import { inject } from '@angular/core';
import { TuiChip } from '@taiga-ui/kit';
import { AUTH_SERVICE_TOKEN } from '../../../../global.provider';
import { UserInfo } from '../../../../services/auth.service';
import { Reservation } from '../../services/reservations.service';
import { Space } from '../../services/spaces.service';
import { RESERVATIONS_SERVICE_TOKEN, SPACES_SERVICE_TOKEN } from '../../spaces.provider';

@Component({
    selector: 'app-reservation-cancel',
    standalone: true,
    imports: [
        CommonModule,
        TranslateModule,
        TuiButton,
        TuiIcon,
        TuiLoader,
        TuiChip
    ],
    templateUrl: './reservation-cancel.component.html',
    styleUrls: ['./reservation-cancel.component.scss']
})
export class ReservationCancelComponent implements OnInit {
    private spacesService = inject(SPACES_SERVICE_TOKEN);
    private reservationsService = inject(RESERVATIONS_SERVICE_TOKEN);
    private authService = inject(AUTH_SERVICE_TOKEN);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private translate = inject(TranslateService);
    private alertService = inject(TuiAlertService);

    reservation = signal<Reservation | null>(null);
    space = signal<Space | null>(null);
    loading = signal(false);
    error = signal<string | null>(null);
    currentUser!: UserInfo;

    ngOnInit(): void {
        this.currentUser = this.authService.getCurrentUserInfo();
        this.route.queryParams.subscribe(params => {
            const reservationId = params['reservation_id'];
            const sessionId = params['session_id'];

            if (reservationId) {
                if (sessionId) {
                    this.confirmPaymentStatus(reservationId, sessionId, 'cancelled');
                }
                this.loadReservationDetails(reservationId);
            } else {
                this.error.set(this.translate.instant('reservations.cancel.reservationNotFound'));
            }
        });
    }

    private loadReservationDetails(reservationId: string): void {
        this.loading.set(true);
        this.error.set(null);

        this.reservationsService.getReservationById(reservationId).subscribe({
            next: (reservation: Reservation | undefined) => {
                if (reservation) {
                    this.reservation.set(reservation);
                    // Load space details
                    this.loadSpaceDetails(reservation.spaceId);
                } else {
                    this.error.set(this.translate.instant('reservations.cancel.reservationNotFound'));
                    this.loading.set(false);
                }
            },
            error: (error: any) => {
                console.error('Error loading reservation:', error);
                this.error.set(this.translate.instant('reservations.cancel.loadError'));
                this.loading.set(false);
            }
        });
    }

    private loadSpaceDetails(spaceId: string): void {
        this.spacesService.getSpaceById(spaceId).subscribe({
            next: (space: Space | undefined) => {
                this.space.set(space || null);
                this.loading.set(false);
            },
            error: (error: any) => {
                console.error('Error loading space:', error);
                this.error.set(this.translate.instant('spaces.detail.notFound'));
                this.loading.set(false);
            }
        });
    }

    onRetryPayment(): void {
        const reservation = this.reservation();
        if (reservation) {
            this.router.navigate(['/spaces/reservations', reservation.id]);
        }
    }

    onBackToSpaces(): void {
        this.router.navigate(['/spaces']);
    }

    onViewReservations(): void {
        this.router.navigate(['/spaces/reservations']);
    }

    formatDate(dateString: string): string {
        return new Date(dateString).toLocaleDateString('fr-FR');
    }

    getSpaceTypeLabel(type: string): string {
        return this.translate.instant(`spaces.spaceType.${type.toLowerCase()}`);
    }

    private confirmPaymentStatus(reservationId: string, sessionId: string, status: 'success' | 'cancelled'): void {
        this.reservationsService.confirmPaymentStatus(reservationId, sessionId, status).subscribe({
            next: (response) => {
                console.log('Payment status confirmed:', response);
            },
            error: (error) => {
                console.error('Error confirming payment status:', error);
                // Don't show error to user as this is a background operation
            }
        });
    }
}
