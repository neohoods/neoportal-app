import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { delay, map } from 'rxjs/operators';
import { ReservationsLoader } from '../../../../mock/reservations-loader';
import { fromApiReservation, UIReservation } from '../../../../models/UIReservation';
import { AdminReservationsService } from '../reservations.service';

@Injectable({
    providedIn: 'root',
})
export class MockAdminReservationsService implements AdminReservationsService {
    constructor(private reservationsLoader: ReservationsLoader) { }

    getReservations(page: number = 0, size: number = 10, status?: string, spaceType?: string, spaceId?: string, userId?: string): Observable<{ content: UIReservation[], totalElements: number, totalPages: number, size: number, number: number, first: boolean, last: boolean, numberOfElements: number }> {
        return this.reservationsLoader.getAllReservations().pipe(
            delay(300),
            (source) => {
                return new Observable(observer => {
                    source.subscribe({
                        next: (reservations) => {
                            // Apply filters
                            let filteredReservations = reservations;

                            if (status) {
                                const statuses = status.split(',').map(s => s.trim());
                                filteredReservations = filteredReservations.filter(reservation =>
                                    statuses.includes(reservation.status)
                                );
                            }

                            if (spaceType) {
                                filteredReservations = filteredReservations.filter(reservation =>
                                    reservation.space?.type === spaceType
                                );
                            }

                            if (spaceId) {
                                filteredReservations = filteredReservations.filter(reservation =>
                                    reservation.spaceId === spaceId
                                );
                            }

                            if (userId) {
                                filteredReservations = filteredReservations.filter(reservation =>
                                    reservation.userId === userId
                                );
                            }

                            // Apply pagination
                            const totalElements = filteredReservations.length;
                            const totalPages = Math.ceil(totalElements / size);
                            const startIndex = page * size;
                            const endIndex = startIndex + size;
                            const content = filteredReservations.slice(startIndex, endIndex);

                            const result = {
                                content: content.map(fromApiReservation),
                                totalElements,
                                totalPages,
                                size,
                                number: page,
                                first: page === 0,
                                last: page >= totalPages - 1,
                                numberOfElements: content.length
                            };

                            observer.next(result);
                            observer.complete();
                        },
                        error: (error) => observer.error(error)
                    });
                });
            }
        );
    }

    getReservation(id: string): Observable<UIReservation> {
        return this.reservationsLoader.getReservationById(id).pipe(
            delay(300),
            map(reservation => {
                if (!reservation) {
                    throw new Error('Reservation not found');
                }
                return fromApiReservation(reservation);
            })
        );
    }

    updateReservationStatus(id: string, status: string): Observable<UIReservation> {
        return this.reservationsLoader.getReservationById(id).pipe(
            delay(500),
            map(reservation => {
                if (!reservation) {
                    throw new Error('Reservation not found');
                }
                const updatedReservation = fromApiReservation({
                    ...reservation,
                    status: status as any,
                    updatedAt: new Date().toISOString()
                });
                return updatedReservation;
            })
        );
    }

    cancelReservation(id: string): Observable<{ message: string; refundAmount: number }> {
        return this.reservationsLoader.cancelReservation(id).pipe(delay(500));
    }

    generateAccessCode(id: string): Observable<{ code: string; generatedAt: string; expiresAt: string; isActive: boolean }> {
        return this.reservationsLoader.generateAccessCode(id).pipe(delay(300));
    }

    getReservationsCount(status?: string, spaceType?: string, userId?: string): Observable<number> {
        return this.reservationsLoader.getAllReservations().pipe(
            delay(200),
            map(reservations => {
                // Apply filters
                let filteredReservations = reservations;

                if (status) {
                    const statuses = status.split(',').map(s => s.trim());
                    filteredReservations = filteredReservations.filter(reservation =>
                        statuses.includes(reservation.status)
                    );
                }

                if (spaceType) {
                    filteredReservations = filteredReservations.filter(reservation =>
                        reservation.space?.type === spaceType
                    );
                }

                if (userId) {
                    filteredReservations = filteredReservations.filter(reservation =>
                        reservation.userId === userId
                    );
                }

                return filteredReservations.length;
            })
        );
    }
}
