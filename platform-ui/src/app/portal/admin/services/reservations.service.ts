import { Observable } from 'rxjs';
import { UIReservation } from '../../../models/UIReservation';

export interface AdminReservationsService {
    getReservations(page?: number, size?: number, status?: string, spaceType?: string, spaceId?: string, userId?: string): Observable<{ content: UIReservation[], totalElements: number, totalPages: number, size: number, number: number, first: boolean, last: boolean, numberOfElements: number }>;
    getReservation(id: string): Observable<UIReservation>;
    updateReservationStatus(id: string, status: string): Observable<UIReservation>;
    cancelReservation(id: string): Observable<{ message: string; refundAmount: number }>;
    generateAccessCode(id: string): Observable<{ code: string; generatedAt: string; expiresAt: string; isActive: boolean }>;
    getReservationsCount(status?: string, spaceType?: string, userId?: string): Observable<number>;
}
