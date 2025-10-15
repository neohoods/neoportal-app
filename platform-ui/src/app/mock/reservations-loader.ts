import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { ReservationStatus } from '../api-client/model/reservationStatus';
import {
    AccessCode,
    CreateReservationRequest,
    CreateReservationResponse,
    Reservation,
    ReservationFeedback,
    ReservationFeedbackRequest
} from '../portal/spaces/services/reservations.service';
import reservationsData from './reservations.json';

@Injectable({
    providedIn: 'root'
})
export class ReservationsLoader {
    private reservations: Reservation[] = [];
    private reservationsLoaded = false;

    constructor() { }

    private loadReservations(): Reservation[] {
        if (this.reservationsLoaded) {
            return this.reservations;
        }

        this.reservations = reservationsData as Reservation[];
        this.reservationsLoaded = true;
        return this.reservations;
    }

    getAllReservations(): Observable<Reservation[]> {
        return of(this.loadReservations()).pipe(delay(500));
    }

    getReservationById(id: string): Observable<Reservation | undefined> {
        const reservations = this.loadReservations();
        const reservation = reservations.find(r => r.id === id);
        return of(reservation).pipe(delay(300));
    }

    getReservationsByUserId(userId: string): Observable<Reservation[]> {
        const reservations = this.loadReservations();
        const filteredReservations = reservations.filter(r => r.userId === userId);
        return of(filteredReservations).pipe(delay(300));
    }

    getReservationsBySpaceId(spaceId: string): Observable<Reservation[]> {
        const reservations = this.loadReservations();
        const filteredReservations = reservations.filter(r => r.spaceId === spaceId);
        return of(filteredReservations).pipe(delay(300));
    }

    getReservationsByStatus(status: string): Observable<Reservation[]> {
        const reservations = this.loadReservations();
        const filteredReservations = reservations.filter(r => r.status === status);
        return of(filteredReservations).pipe(delay(300));
    }

    createReservation(request: CreateReservationRequest): Observable<CreateReservationResponse> {
        const reservations = this.loadReservations();

        // Mock implementation - create a new reservation
        const newReservation: Reservation = {
            id: `res-${Date.now()}`,
            spaceId: request.spaceId,
            userId: "user-001", // Mock current user
            startDate: request.startDate,
            endDate: request.endDate,
            status: ReservationStatus.PendingPayment,
            totalPrice: 0, // Will be calculated
            currency: "EUR",
            cleaningFee: 0,
            deposit: 0,
            stripeSessionId: `cs_test_${Date.now()}`,
            stripePaymentIntentId: undefined,
            accessCode: undefined,
            feedback: undefined,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString()
        };

        // Add to our mock data
        this.reservations.push(newReservation);

        const response: CreateReservationResponse = {
            reservation: newReservation,
        };

        return of(response).pipe(delay(800));
    }

    cancelReservation(id: string): Observable<{ message: string; refundAmount: number }> {
        const reservations = this.loadReservations();
        const reservation = reservations.find(r => r.id === id);

        if (!reservation) {
            throw new Error('Reservation not found');
        }

        reservation.status = ReservationStatus.Cancelled;
        reservation.updatedAt = new Date().toISOString();

        const result = {
            message: "Reservation cancelled successfully",
            refundAmount: reservation.totalPrice
        };

        return of(result).pipe(delay(500));
    }

    submitFeedback(id: string, feedback: ReservationFeedbackRequest): Observable<void> {
        const reservations = this.loadReservations();
        const reservation = reservations.find(r => r.id === id);

        if (!reservation) {
            throw new Error('Reservation not found');
        }

        const newFeedback: ReservationFeedback = {
            id: `feedback-${Date.now()}`,
            rating: feedback.rating,
            comment: feedback.comment,
            cleanliness: feedback.cleanliness,
            communication: feedback.communication,
            value: feedback.value,
            submittedAt: new Date().toISOString()
        };

        reservation.feedback = newFeedback;
        reservation.updatedAt = new Date().toISOString();

        return of(void 0).pipe(delay(500));
    }

    generateAccessCode(id: string): Observable<AccessCode> {
        const reservations = this.loadReservations();
        const reservation = reservations.find(r => r.id === id);

        if (!reservation) {
            throw new Error('Reservation not found');
        }

        const newAccessCode: AccessCode = {
            code: this.generateRandomCode(),
            generatedAt: new Date().toISOString(),
            expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(), // 7 days from now
            isActive: true
        };

        reservation.accessCode = newAccessCode;
        reservation.updatedAt = new Date().toISOString();

        return of(newAccessCode).pipe(delay(300));
    }

    private generateRandomCode(): string {
        const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
        let result = '';
        for (let i = 0; i < 6; i++) {
            result += chars.charAt(Math.floor(Math.random() * chars.length));
        }
        return result;
    }
}
