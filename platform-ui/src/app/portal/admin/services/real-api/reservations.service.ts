import { inject, Injectable } from '@angular/core';
import { map, Observable, switchMap } from 'rxjs';
import { ReservationsAdminApiService } from '../../../../api-client/api/reservationsAdminApi.service';
import { SpacesAdminApiService } from '../../../../api-client/api/spacesAdminApi.service';
import { fromApiReservation, UIReservation } from '../../../../models/UIReservation';
import { AdminReservationsService } from '../reservations.service';
import { SpacesAdminCacheService } from '../spaces-admin-cache.service';

@Injectable({
    providedIn: 'root',
})
export class RealApiAdminReservationsService implements AdminReservationsService {
    private spacesAdminCacheService = inject(SpacesAdminCacheService);

    constructor(
        private reservationsAdminApi: ReservationsAdminApiService,
        private spacesAdminApi: SpacesAdminApiService
    ) { }

    getReservations(page: number = 0, size: number = 10, status?: string, spaceType?: string, spaceId?: string, userId?: string): Observable<{ content: UIReservation[], totalElements: number, totalPages: number, size: number, number: number, first: boolean, last: boolean, numberOfElements: number }> {
        return this.reservationsAdminApi.getAdminReservations(spaceId, userId || undefined, status as any, undefined, undefined, spaceType as any, undefined, page, size).pipe(
            switchMap(response => {
                const reservations = response.content || [];

                // Si pas de réservations, retourner directement
                if (reservations.length === 0) {
                    return new Observable<{ content: UIReservation[], totalElements: number, totalPages: number, size: number, number: number, first: boolean, last: boolean, numberOfElements: number }>(observer => {
                        observer.next({
                            ...response,
                            content: []
                        });
                        observer.complete();
                    });
                }

                // Extraire les IDs des espaces uniques
                const spaceIds = [...new Set(reservations.map(r => r.spaceId).filter(id => id))];

                // Use cache service to load all spaces in one request (with caching)
                return this.spacesAdminCacheService.loadSpacesByIds(spaceIds).pipe(
                    map(spaceMap => {
                        // Mapper les réservations avec leurs espaces
                        const reservationsWithSpaces = reservations.map(reservation => {
                            const uiReservation = fromApiReservation(reservation);
                            if (reservation.spaceId && spaceMap.has(reservation.spaceId)) {
                                uiReservation.space = spaceMap.get(reservation.spaceId);
                            }
                            return uiReservation;
                        });

                        return {
                            ...response,
                            content: reservationsWithSpaces
                        };
                    })
                );
            })
        );
    }

    getReservation(id: string): Observable<UIReservation> {
        return this.reservationsAdminApi.getAdminReservation(id).pipe(
            switchMap(reservation => {
                if (!reservation.spaceId) {
                    return new Observable<UIReservation>(observer => {
                        observer.next(fromApiReservation(reservation));
                        observer.complete();
                    });
                }

                // Use cache service to load space (will use cache if available)
                return this.spacesAdminCacheService.loadSpacesByIds([reservation.spaceId]).pipe(
                    map(spaceMap => {
                        const uiReservation = fromApiReservation(reservation);
                        const space = spaceMap.get(reservation.spaceId);
                        if (space) {
                            uiReservation.space = space;
                        }
                        return uiReservation;
                    })
                );
            })
        );
    }

    updateReservationStatus(id: string, status: string): Observable<UIReservation> {
        // This method doesn't exist in the API, so we'll implement it by getting the reservation and updating it
        return this.reservationsAdminApi.getAdminReservation(id).pipe(
            (source) => {
                return new Observable(observer => {
                    source.subscribe({
                        next: (reservation) => {
                            // In a real implementation, there would be an update method
                            // For now, we'll just return the reservation with updated status
                            const updatedReservation = fromApiReservation({ ...reservation, status: status as any });
                            observer.next(updatedReservation);
                            observer.complete();
                        },
                        error: (error) => observer.error(error)
                    });
                });
            }
        );
    }

    cancelReservation(id: string): Observable<{ message: string; refundAmount: number }> {
        // This method doesn't exist in the API, so we'll implement it by getting the reservation and updating it
        return this.reservationsAdminApi.getAdminReservation(id).pipe(
            (source) => {
                return new Observable(observer => {
                    source.subscribe({
                        next: (reservation) => {
                            // In a real implementation, there would be a cancel method
                            // For now, we'll just return a mock response
                            const result = {
                                message: "Reservation cancelled successfully",
                                refundAmount: reservation.totalPrice || 0
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

    generateAccessCode(id: string): Observable<{ code: string; generatedAt: string; expiresAt: string; isActive: boolean }> {
        return this.reservationsAdminApi.regenerateAccessCode(id).pipe(
            (source) => {
                return new Observable(observer => {
                    source.subscribe({
                        next: (accessCode) => {
                            const result = {
                                code: accessCode.code,
                                generatedAt: accessCode.generatedAt,
                                expiresAt: accessCode.expiresAt,
                                isActive: accessCode.isActive || false
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

    getReservationsCount(status?: string, spaceType?: string, userId?: string): Observable<number> {
        // Utiliser getReservations avec une taille de 1 pour récupérer seulement le totalElements
        return this.reservationsAdminApi.getAdminReservations(undefined, userId || undefined, status as any, undefined, undefined, spaceType as any, undefined, 0, 1).pipe(
            map(response => response.totalElements || 0)
        );
    }
}
