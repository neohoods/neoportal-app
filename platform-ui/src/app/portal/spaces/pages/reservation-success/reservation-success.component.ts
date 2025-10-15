import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiLoader } from '@taiga-ui/core';
import { interval, Subscription } from 'rxjs';
import { takeWhile } from 'rxjs/operators';

import { inject } from '@angular/core';
import { TuiChip } from '@taiga-ui/kit';
import { AUTH_SERVICE_TOKEN } from '../../../../global.provider';
import { UserInfo } from '../../../../services/auth.service';
import { Reservation } from '../../services/reservations.service';
import { Space } from '../../services/spaces.service';
import { RESERVATIONS_SERVICE_TOKEN, SPACES_SERVICE_TOKEN } from '../../spaces.provider';

@Component({
    selector: 'app-reservation-success',
    standalone: true,
    imports: [
        CommonModule,
        TranslateModule,
        RouterLink,
        TuiButton,
        TuiIcon,
        TuiLoader,
        TuiChip
    ],
    templateUrl: './reservation-success.component.html',
    styleUrls: ['./reservation-success.component.scss']
})
export class ReservationSuccessComponent implements OnInit, OnDestroy {
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
    paymentConfirmed = signal(false);
    pollingSubscription?: Subscription;

    ngOnInit(): void {
        this.currentUser = this.authService.getCurrentUserInfo();

        this.route.queryParams.subscribe(params => {
            const sessionId = params['session_id'];
            const reservationId = params['reservation_id'];
            if (sessionId && reservationId) {
                this.confirmPaymentStatus(reservationId, sessionId, 'success');
                this.loadReservationDetails(reservationId);
            } else {
                this.error.set(this.translate.instant('reservations.success.reservationNotFound'));
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
                    this.error.set(this.translate.instant('reservations.success.reservationNotFound'));
                    this.loading.set(false);
                }
            },
            error: (error: any) => {
                console.error('Error loading reservation:', error);
                this.error.set(this.translate.instant('reservations.success.loadError'));
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

    onViewReservations(): void {
        this.router.navigate(['/spaces/reservations']);
    }

    onBackToSpaces(): void {
        this.router.navigate(['/spaces']);
    }

    formatDate(dateString: string): string {
        return new Date(dateString).toLocaleDateString('fr-FR');
    }

    getSpaceTypeLabel(type: string): string {
        return this.translate.instant(`spaces.spaceType.${type.toLowerCase()}`);
    }

    ngOnDestroy(): void {
        if (this.pollingSubscription) {
            this.pollingSubscription.unsubscribe();
        }
    }

    private confirmPaymentStatus(reservationId: string, sessionId: string, status: 'success' | 'cancelled'): void {
        this.reservationsService.confirmPaymentStatus(reservationId, sessionId, status).subscribe({
            next: (response) => {
                console.log('Payment status confirmed:', response);
                this.paymentConfirmed.set(true);

                // Start polling to check if reservation status has been updated
                this.startPollingForConfirmation(reservationId);
            },
            error: (error) => {
                console.error('Error confirming payment status:', error);
                // Don't show error to user as this is a background operation
            }
        });
    }

    private startPollingForConfirmation(reservationId: string): void {
        // Poll every 2 seconds for up to 30 seconds (15 attempts)
        this.pollingSubscription = interval(2000)
            .pipe(takeWhile(() => !this.paymentConfirmed()))
            .subscribe(() => {
                this.reservationsService.getReservationById(reservationId).subscribe({
                    next: (reservation: Reservation | undefined) => {
                        if (reservation && reservation.status === 'CONFIRMED') {
                            this.paymentConfirmed.set(true);
                            this.reservation.set(reservation);
                            this.pollingSubscription?.unsubscribe();
                        }
                    },
                    error: (error) => {
                        console.error('Error polling reservation status:', error);
                    }
                });
            });
    }
}
