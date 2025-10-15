import { Observable } from 'rxjs';
import { UIReservation } from '../../../models/UIReservation';

export interface AccessCode {
  code: string;
  generatedAt: string;
  expiresAt: string;
  isActive: boolean;
}

export interface ReservationFeedback {
  id: string;
  rating: number;
  comment: string;
  cleanliness?: number;
  communication?: number;
  value?: number;
  submittedAt: string;
}

// Legacy type alias for backward compatibility
export type Reservation = UIReservation;

export interface CreateReservationRequest {
  spaceId: string;
  startDate: string;
  endDate: string;
  specialRequests?: string;
}

export interface CreateReservationResponse {
  reservation: UIReservation;
}

export interface PaymentSessionResponse {
  stripeCheckoutUrl: string;
  paymentIntentId: string;
}

export interface ReservationFeedbackRequest {
  rating: number;
  comment: string;
  cleanliness?: number;
  communication?: number;
  value?: number;
}

export interface QuotaResponse {
  spaceType: 'GUEST_ROOM' | 'COMMON_ROOM' | 'COWORKING' | 'PARKING';
  used: number;
  max: number;
  remaining: number;
  period: string;
  resetDate?: string;
}

export interface PaymentConfirmationResponse {
  reservationId: string;
  status: 'PAID' | 'PENDING_PAYMENT' | 'CANCELLED';
  message: string;
}

export interface ReservationsService {
  getMyReservations(status?: string, spaceType?: string): Observable<UIReservation[]>;
  getReservationById(id: string): Observable<UIReservation | undefined>;
  createReservation(request: CreateReservationRequest): Observable<CreateReservationResponse>;
  cancelReservation(id: string): Observable<{ message: string; refundAmount: number }>;
  submitFeedback(id: string, feedback: ReservationFeedbackRequest): Observable<void>;
  initiatePayment(reservationId: string): Observable<PaymentSessionResponse>;
  confirmPaymentStatus(reservationId: string, sessionId: string, status: 'success' | 'cancelled'): Observable<PaymentConfirmationResponse>;
}
