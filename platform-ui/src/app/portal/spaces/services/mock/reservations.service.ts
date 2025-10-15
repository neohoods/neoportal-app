import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AUTH_SERVICE_TOKEN } from '../../../../global.provider';
import { ReservationsLoader } from '../../../../mock/reservations-loader';
import {
    CreateReservationRequest,
    CreateReservationResponse,
    PaymentConfirmationResponse,
    PaymentSessionResponse,
    Reservation,
    ReservationFeedbackRequest,
    ReservationsService
} from '../reservations.service';

@Injectable({
    providedIn: 'root'
})
export class MockReservationsService implements ReservationsService {
    private authService = inject(AUTH_SERVICE_TOKEN);

    constructor(private reservationsLoader: ReservationsLoader) { }

    getMyReservations(status?: string, spaceType?: string): Observable<Reservation[]> {
        const currentUser = this.authService.getCurrentUserInfo();
        const userId = currentUser.user.id;
        return this.reservationsLoader.getReservationsByUserId(userId);
    }

    getReservationById(id: string): Observable<Reservation | undefined> {
        return this.reservationsLoader.getReservationById(id);
    }

    createReservation(request: CreateReservationRequest): Observable<CreateReservationResponse> {
        return this.reservationsLoader.createReservation(request);
    }

    cancelReservation(id: string): Observable<{ message: string; refundAmount: number }> {
        return this.reservationsLoader.cancelReservation(id);
    }

    submitFeedback(id: string, feedback: ReservationFeedbackRequest): Observable<void> {
        return this.reservationsLoader.submitFeedback(id, feedback);
    }

    initiatePayment(reservationId: string): Observable<PaymentSessionResponse> {
        // Mock implementation - return a fake Stripe checkout URL
        return new Observable(observer => {
            setTimeout(() => {
                observer.next({
                    stripeCheckoutUrl: `https://checkout.stripe.com/c/pay/cs_test_${reservationId}`,
                    paymentIntentId: `pi_${reservationId}`
                });
                observer.complete();
            }, 500);
        });
    }

    confirmPaymentStatus(reservationId: string, sessionId: string, status: 'success' | 'cancelled'): Observable<PaymentConfirmationResponse> {
        // Mock implementation - simulate payment confirmation
        return new Observable(observer => {
            setTimeout(() => {
                const response: PaymentConfirmationResponse = {
                    reservationId,
                    status: status === 'success' ? 'PAID' : 'CANCELLED',
                    message: status === 'success'
                        ? 'Payment confirmed successfully'
                        : 'Payment was cancelled'
                };
                observer.next(response);
                observer.complete();
            }, 300);
        });
    }
}
