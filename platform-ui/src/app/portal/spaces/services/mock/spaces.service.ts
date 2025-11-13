import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay, map } from 'rxjs/operators';
import { ReservationConflict } from '../../../../api-client/model/models';
import { SpacesLoader } from '../../../../mock/spaces-loader';
import {
    AvailabilityResponse,
    PaginatedSpacesResponse,
    Space,
    SpacesService
} from '../spaces.service';

@Injectable({
    providedIn: 'root'
})
export class MockSpacesService implements SpacesService {
    private cache = new Map<string, Space>();

    constructor(private spacesLoader: SpacesLoader) { }

    getSpaces(type?: string, available?: boolean, startDate?: string, endDate?: string, search?: string, page?: number, size?: number): Observable<PaginatedSpacesResponse> {
        return this.spacesLoader.getAllSpaces().pipe(
            map(spaces => {
                const totalElements = spaces.length;
                const totalPages = Math.ceil(totalElements / (size || 12));
                const currentPage = (page || 0) + 1; // Convert to 1-based
                const startIndex = (page || 0) * (size || 12);
                const endIndex = startIndex + (size || 12);
                const paginatedSpaces = spaces.slice(startIndex, endIndex);

                return {
                    content: paginatedSpaces,
                    totalElements,
                    totalPages,
                    size: size || 12,
                    number: page || 0,
                    first: (page || 0) === 0,
                    last: (page || 0) >= totalPages - 1,
                    numberOfElements: paginatedSpaces.length
                };
            })
        );
    }

    getSpaceById(id: string): Observable<Space | undefined> {
        return this.spacesLoader.getSpaceById(id);
    }

    getSpaceAvailability(spaceId: string, startDate: string, endDate: string): Observable<AvailabilityResponse> {
        return this.spacesLoader.getSpaceById(spaceId).pipe(
            map((space: Space | undefined) => {
                if (!space) {
                    return {
                        available: false,
                        conflicts: [{
                            type: 'SAME_DAY_BLOCK' as any,
                            message: 'Espace non trouvé'
                        }],
                        quota: {
                            max: 0,
                            period: 'YEAR' as any,
                            resetDate: '2024-01-01'
                        },
                        price: 0,
                        currency: 'EUR'
                    };
                }

                // Mock availability logic
                const conflicts: ReservationConflict[] = [];
                let available = true;

                // Check if space is active
                if (space.status !== 'ACTIVE') {
                    available = false;
                    conflicts.push({
                        type: 'SAME_DAY_BLOCK' as any,
                        message: 'Espace non disponible'
                    });
                }

                // Quota check removed - 'used' field no longer exists

                // Mock price calculation
                const price = space.pricing.tenantPrice; // In real app, would check user type

                return {
                    available,
                    conflicts,
                    quota: space.quota || {
                        max: 0,
                        period: 'YEAR' as any,
                        resetDate: '2024-01-01'
                    },
                    price,
                    currency: space.pricing.currency
                };
            }),
            delay(800)
        );
    }

    getSharedSpaceReservations(spaceId: string, startDate: string, endDate: string): Observable<any[]> {
        // Mock implementation - return empty array for now
        return of([]).pipe(delay(300));
    }

    getPriceBreakdown(spaceId: string, startDate: string, endDate: string): Observable<any> {
        return this.getSpaceById(spaceId).pipe(
            map((space: Space | undefined) => {
                if (!space) {
                    throw new Error('Space not found');
                }

                // Calculate number of days
                const start = new Date(startDate);
                const end = new Date(endDate);
                const numberOfDays = Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1;

                // Mock price calculation
                const unitPrice = space.pricing.tenantPrice || 0;
                const totalDaysPrice = unitPrice * numberOfDays;
                const cleaningFee = space.pricing.cleaningFee || 0;
                const subtotal = totalDaysPrice + cleaningFee;
                const deposit = space.pricing.deposit || 0;
                const platformFeeAmount = subtotal * 0.1; // 10% mock
                const platformFixedFeeAmount = 0.25; // Mock fixed fee
                const totalPrice = subtotal + deposit + platformFeeAmount + platformFixedFeeAmount;

                return {
                    unitPrice,
                    numberOfDays,
                    totalDaysPrice,
                    cleaningFee,
                    subtotal,
                    deposit,
                    platformFeeAmount,
                    platformFixedFeeAmount,
                    totalPrice
                };
            }),
            delay(300)
        );
    }

    // Cache methods
    loadSpacesByIds(spaceIds: string[]): Observable<Map<string, Space>> {
        if (spaceIds.length === 0) {
            return of(new Map());
        }

        const uniqueSpaceIds = [...new Set(spaceIds)];
        const result = new Map<string, Space>();
        const missingIds: string[] = [];

        // Check cache
        uniqueSpaceIds.forEach(id => {
            const cachedSpace = this.cache.get(id);
            if (cachedSpace) {
                result.set(id, cachedSpace);
            } else {
                missingIds.push(id);
            }
        });

        // If all spaces are cached, return immediately
        if (missingIds.length === 0) {
            return of(result);
        }

        // Load missing spaces from loader
        return this.spacesLoader.getAllSpaces().pipe(
            map(spaces => {
                // Add missing spaces to cache and result
                missingIds.forEach(id => {
                    const space = spaces.find(s => s.id === id);
                    if (space) {
                        this.cache.set(id, space);
                        result.set(id, space);
                    }
                });
                return result;
            })
        );
    }

    getCachedSpace(id: string): Space | undefined {
        return this.cache.get(id);
    }

    clearSpacesCache(): void {
        this.cache.clear();
    }

    preloadAllSpaces(): Observable<void> {
        return this.spacesLoader.getAllSpaces().pipe(
            map(spaces => {
                spaces.forEach(space => {
                    this.cache.set(space.id, space);
                });
            })
        );
    }
}
