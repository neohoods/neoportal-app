import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ReservationsApiService } from '../../../../api-client/api/reservationsApi.service';
import { UIReservation } from '../../../../models/UIReservation';
import {
  CreateReservationRequest,
  CreateReservationResponse,
  PaymentConfirmationResponse,
  PaymentSessionResponse,
  ReservationFeedbackRequest,
  ReservationsService
} from '../reservations.service';

@Injectable({
  providedIn: 'root'
})
export class APIReservationsService implements ReservationsService {

  constructor(private reservationsApi: ReservationsApiService) { }

  private fromApiReservation(apiReservation: any): UIReservation {
    return {
      id: apiReservation.id || '',
      spaceId: apiReservation.spaceId || '',
      userId: apiReservation.userId || '',
      startDate: apiReservation.startDate || '',
      endDate: apiReservation.endDate || '',
      status: apiReservation.status || 'PENDING_PAYMENT',
      totalPrice: apiReservation.totalPrice || 0,
      currency: apiReservation.currency || 'EUR',
      cleaningFee: apiReservation.cleaningFee || 0,
      deposit: apiReservation.deposit || 0,
      platformFeeAmount: apiReservation.platformFeeAmount,
      platformFixedFeeAmount: apiReservation.platformFixedFeeAmount,
      stripeSessionId: apiReservation.stripeSessionId,
      stripePaymentIntentId: apiReservation.stripePaymentIntentId,
      accessCode: apiReservation.accessCode,
      feedback: apiReservation.feedback,
      space: apiReservation.space,
      createdAt: apiReservation.createdAt || new Date().toISOString(),
      updatedAt: apiReservation.updatedAt || new Date().toISOString()
    };
  }

  getMyReservations(status?: string, spaceType?: string): Observable<UIReservation[]> {
    return this.reservationsApi.getMyReservations(status as any, spaceType as any).pipe(
      map(response => (response.content || []).map(this.fromApiReservation))
    );
  }

  getReservationById(id: string): Observable<UIReservation | undefined> {
    return this.reservationsApi.getReservation(id).pipe(
      map(reservation => reservation ? this.fromApiReservation(reservation) : undefined)
    );
  }

  createReservation(request: CreateReservationRequest): Observable<CreateReservationResponse> {
    return this.reservationsApi.createReservation(request).pipe(
      map(response => ({
        reservation: this.fromApiReservation(response.reservation)
      }))
    );
  }

  cancelReservation(id: string): Observable<{ message: string; refundAmount: number }> {
    return this.reservationsApi.cancelReservation(id).pipe(
      map(response => ({
        message: response.message || 'Reservation cancelled successfully',
        refundAmount: response.refundAmount || 0
      }))
    );
  }

  submitFeedback(id: string, feedback: ReservationFeedbackRequest): Observable<void> {
    return this.reservationsApi.submitReservationFeedback(id, feedback).pipe(
      map(() => undefined)
    );
  }

  initiatePayment(reservationId: string): Observable<PaymentSessionResponse> {
    return this.reservationsApi.initiatePayment(reservationId).pipe(
      map((response: any) => ({
        stripeCheckoutUrl: response.stripeCheckoutUrl,
        paymentIntentId: response.paymentIntentId
      }))
    );
  }

  confirmPaymentStatus(reservationId: string, sessionId: string, status: 'success' | 'cancelled'): Observable<PaymentConfirmationResponse> {
    return this.reservationsApi.confirmReservationPayment(reservationId, {
      sessionId,
      status: status as any
    }).pipe(
      map((response: any) => ({
        reservationId: response.reservationId,
        status: response.status,
        message: response.message
      }))
    );
  }
}
