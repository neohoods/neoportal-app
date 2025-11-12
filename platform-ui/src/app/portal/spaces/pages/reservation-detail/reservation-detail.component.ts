import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiLoader } from '@taiga-ui/core';
import { TuiChip } from '@taiga-ui/kit';

import { inject } from '@angular/core';
import { Reservation } from '../../services/reservations.service';
import { Space } from '../../services/spaces.service';
import { RESERVATIONS_SERVICE_TOKEN, SPACES_SERVICE_TOKEN } from '../../spaces.provider';

@Component({
    selector: 'app-reservation-detail',
    standalone: true,
    imports: [
        CommonModule,
        TranslateModule,
        TuiButton,
        TuiIcon,
        TuiLoader,
        TuiChip
    ],
    templateUrl: './reservation-detail.component.html',
    styleUrls: ['./reservation-detail.component.scss']
})
export class ReservationDetailComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private reservationsService = inject(RESERVATIONS_SERVICE_TOKEN);
    private spacesService = inject(SPACES_SERVICE_TOKEN);
    private translate = inject(TranslateService);
    private alertService = inject(TuiAlertService);

    reservation = signal<Reservation | null>(null);
    space = signal<Space | null>(null);
    loading = signal(true);
    error = signal<string | null>(null);

    // Payment timer signals
    timeRemaining = signal<string | null>(null);
    isExpired = signal<boolean>(false);
    private timerInterval: any;

    ngOnInit(): void {
        const reservationId = this.route.snapshot.paramMap.get('id');
        if (reservationId) {
            this.loadReservationDetails(reservationId);
        } else {
            this.error.set(this.translate.instant('reservations.notFound'));
            this.loading.set(false);
        }
    }

    private loadReservationDetails(reservationId: string): void {
        this.loading.set(true);
        this.error.set(null);

        this.reservationsService.getReservationById(reservationId).subscribe({
            next: (reservation) => {
                if (reservation) {
                    this.reservation.set(reservation);
                    // Start timer if in payment statuses
                    if (reservation.status === 'PENDING_PAYMENT' || reservation.status === 'PAYMENT_FAILED') {
                        this.startPaymentTimer(reservation);
                    }
                    this.loadSpace(reservation.spaceId);
                } else {
                    this.error.set(this.translate.instant('reservations.notFound'));
                    this.loading.set(false);
                }
            },
            error: (error) => {
                console.error('Error loading reservation:', error);
                this.error.set(this.translate.instant('reservations.loadError'));
                this.loading.set(false);
            }
        });
    }

    private loadSpace(spaceId: string): void {
        this.spacesService.getSpaceById(spaceId).subscribe({
            next: (space) => {
                if (space) {
                    this.space.set(space);
                }
                this.loading.set(false);
            },
            error: (error) => {
                console.error('Error loading space:', error);
                this.loading.set(false);
            }
        });
    }

    onBackClick(): void {
        this.router.navigate(['/spaces/reservations']);
    }

    formatDate(dateString: string): string {
        return new Date(dateString).toLocaleDateString('fr-FR', {
            weekday: 'long',
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        });
    }

    formatTime(dateString: string): string {
        return new Date(dateString).toLocaleTimeString('fr-FR', {
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    getStatusLabel(status: string): string {
        return this.translate.instant(`reservations.status.${status}`);
    }

    getStatusAppearance(status: string): string {
        switch (status) {
            case 'PENDING_PAYMENT':
                return 'warning'; // Orange - en attente de paiement
            case 'PAYMENT_FAILED':
                return 'negative'; // Rouge - échec de paiement
            case 'EXPIRED':
                return 'negative'; // Rouge - expiré
            case 'CONFIRMED':
                return 'positive'; // Vert - confirmé
            case 'ACTIVE':
                return 'accent'; // Bleu - actif
            case 'COMPLETED':
                return 'positive'; // Vert - terminé
            case 'CANCELLED':
                return 'negative'; // Rouge - annulé
            case 'REFUNDED':
                return 'info'; // Bleu clair - remboursé
            default:
                return 'outline'; // Gris - statut inconnu
        }
    }

    getStatusClass(status: string): string {
        switch (status) {
            case 'PENDING_PAYMENT':
                return 'status-pending-payment';
            case 'PAYMENT_FAILED':
                return 'status-payment-failed';
            case 'EXPIRED':
                return 'status-expired';
            case 'CONFIRMED':
                return 'status-confirmed';
            case 'ACTIVE':
                return 'status-active';
            case 'COMPLETED':
                return 'status-completed';
            case 'CANCELLED':
                return 'status-cancelled';
            case 'REFUNDED':
                return 'status-refunded';
            default:
                return 'status-unknown';
        }
    }

    getSpaceTypeLabel(type: string): string {
        return this.translate.instant(`spaces.types.${type}`);
    }

    getDuration(): number {
        const reservation = this.reservation();
        if (!reservation) return 0;

        const start = new Date(reservation.startDate);
        const end = new Date(reservation.endDate);
        const diffTime = Math.abs(end.getTime() - start.getTime());
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
        return diffDays;
    }

    getMainImage(): string {
        const space = this.space();
        return space?.images?.[0]?.url || '/assets/images/placeholder-space.jpg';
    }

    onViewSpace(): void {
        const space = this.space();
        if (space) {
            this.router.navigate(['/spaces/detail', space.id]);
        }
    }

    onRetryPayment(): void {
        const reservation = this.reservation();
        if (reservation) {
            this.loading.set(true);
            this.reservationsService.initiatePayment(reservation.id).subscribe({
                next: (response: any) => {
                    this.loading.set(false);
                    if (response.stripeCheckoutUrl) {
                        window.location.href = response.stripeCheckoutUrl;
                    }
                },
                error: (error: any) => {
                    this.loading.set(false);
                    console.error('Error retrying payment:', error);
                    this.alertService.open(
                        this.translate.instant('reservations.payment.retryError'),
                        { appearance: 'negative' }
                    ).subscribe();
                }
            });
        }
    }

    canRetryPayment(): boolean {
        const reservation = this.reservation();
        return reservation?.status === 'PAYMENT_FAILED';
    }

    // Payment view methods
    shouldShowPaymentView(): boolean {
        const reservation = this.reservation();
        return reservation?.status === 'PENDING_PAYMENT' || reservation?.status === 'PAYMENT_FAILED';
    }

    shouldShowRetryButton(): boolean {
        const reservation = this.reservation();
        return reservation?.status === 'PAYMENT_FAILED';
    }

    isFreeReservation(): boolean {
        const reservation = this.reservation();
        return reservation ? reservation.totalPrice === 0 : false;
    }

    calculateDays(): number {
        const reservation = this.reservation();
        if (reservation) {
            const start = new Date(reservation.startDate);
            const end = new Date(reservation.endDate);
            return Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1; // +1 car endDate est inclus
        }
        return 1;
    }

    calculateSubtotal(): number {
        const reservation = this.reservation();
        const space = this.space();
        if (reservation && space) {
            const days = this.calculateDays();
            return space.pricing.tenantPrice * days;
        }
        return 0;
    }

    calculateTotalPrice(): number {
        const reservation = this.reservation();
        return reservation ? reservation.totalPrice : 0;
    }

    calculatePlatformFeeTotal(): number {
        const reservation = this.reservation();
        if (reservation) {
            // Round up to 1 decimal place (same logic as backend: RoundingMode.CEILING)
            // This matches: .divide(BigDecimal.valueOf(100), 1, RoundingMode.CEILING)
            // and: .setScale(1, RoundingMode.CEILING)
            const roundUpToOneDecimal = (value: number): number => {
                if (value === 0 || !value) return 0;
                // Round up to 1 decimal: multiply by 10, ceil, divide by 10
                return Math.ceil(value * 10) / 10;
            };

            const percentageFee = roundUpToOneDecimal(reservation.platformFeeAmount || 0);
            const fixedFee = roundUpToOneDecimal(reservation.platformFixedFeeAmount || 0);
            const total = percentageFee + fixedFee;
            
            // Final rounding to ensure we display with max 1 decimal place
            // Use round to avoid floating point precision issues
            return Math.round(total * 10) / 10;
        }
        return 0;
    }

    formatPlatformFeeTotal(): string {
        const total = this.calculatePlatformFeeTotal();
        // Format to 1 decimal place to avoid floating point precision issues
        return total.toFixed(1);
    }

    getStartDate(): string {
        const reservation = this.reservation();
        return reservation ? reservation.startDate : '';
    }

    getEndDate(): string {
        const reservation = this.reservation();
        return reservation ? reservation.endDate : '';
    }

    getTimeRemainingMinutes(): number {
        const timeStr = this.timeRemaining();
        if (!timeStr) return 0;
        const [minutes] = timeStr.split(':').map(Number);
        return minutes;
    }

    onPayWithStripe(): void {
        const reservation = this.reservation();
        if (reservation) {
            this.reservationsService.initiatePayment(reservation.id).subscribe({
                next: (response: any) => {
                    window.location.href = response.stripeCheckoutUrl;
                },
                error: (error: any) => {
                    console.error('Error initiating payment:', error);
                    this.alertService.open(
                        this.translate.instant('reservations.payment.paymentError'),
                        { appearance: 'negative' }
                    ).subscribe();
                }
            });
        }
    }

    onConfirmReservation(): void {
        const reservation = this.reservation();
        if (reservation) {
            this.loading.set(true);
            this.reservationsService.confirmPaymentStatus(reservation.id, 'free-reservation', 'success').subscribe({
                next: (response: any) => {
                    this.loading.set(false);
                    this.alertService.open(
                        this.translate.instant('reservations.payment.confirmationSuccess'),
                        { appearance: 'positive' }
                    ).subscribe();
                    // Reload the reservation to show updated status
                    this.loadReservationDetails(reservation.id);
                },
                error: (error: any) => {
                    this.loading.set(false);
                    console.error('Error confirming reservation:', error);
                    this.alertService.open(
                        this.translate.instant('reservations.payment.confirmationError'),
                        { appearance: 'negative' }
                    ).subscribe();
                }
            });
        }
    }

    private startPaymentTimer(reservation: Reservation): void {
        // All times in UTC milliseconds
        const now = new Date().getTime();

        let expirationTime: number;
        if (reservation.paymentExpiresAt) {
            expirationTime = new Date(reservation.paymentExpiresAt).getTime();
        } else {
            const createdAt = new Date(reservation.createdAt).getTime();
            expirationTime = createdAt + (15 * 60 * 1000);
        }

        // Check if already expired
        if (expirationTime <= now) {
            this.isExpired.set(true);
            this.timeRemaining.set(null);
            return;
        }

        this.timerInterval = setInterval(() => {
            const now = new Date().getTime();
            const timeLeft = expirationTime - now;

            if (timeLeft <= 0) {
                this.isExpired.set(true);
                this.timeRemaining.set(null);
                clearInterval(this.timerInterval);
            } else {
                const minutes = Math.floor(timeLeft / 60000);
                const seconds = Math.floor((timeLeft % 60000) / 1000);
                this.timeRemaining.set(`${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`);
            }
        }, 1000);
    }

    ngOnDestroy(): void {
        if (this.timerInterval) {
            clearInterval(this.timerInterval);
        }
    }

    // Helper method to check if data is loaded
    isDataLoaded(): boolean {
        return !this.loading() && !this.error() && !!this.reservation() && !!this.space();
    }
}

