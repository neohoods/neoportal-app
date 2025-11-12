import { Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { map, shareReplay } from 'rxjs/operators';
import { SpacesApiService } from '../../../../api-client/api/spacesApi.service';
import { UISpace } from '../../../../models/UISpace';
import { AvailabilityResponse, PaginatedSpacesResponse, SpacesService } from '../spaces.service';

@Injectable({
    providedIn: 'root'
})
export class APISpacesService implements SpacesService {
    private cache = signal<Map<string, UISpace>>(new Map());
    private loadingPromise: Observable<void> | null = null;
    private allSpacesLoaded = signal(false);

    constructor(private spacesApi: SpacesApiService) { }

    private fromApiSpace(apiSpace: any): UISpace {
        return {
            id: apiSpace.id || '',
            name: apiSpace.name || '',
            description: apiSpace.description || '',
            instructions: apiSpace.instructions || '',
            type: apiSpace.type || 'GUEST_ROOM',
            status: apiSpace.status || 'ACTIVE',
            pricing: apiSpace.pricing || { hourly: 0, daily: 0, weekly: 0, monthly: 0 },
            rules: apiSpace.rules || { maxOccupancy: 1, allowedActivities: [], restrictions: [] },
            images: apiSpace.images || [],
            quota: apiSpace.quota || { max: 1, period: 'MONTH' },
            capacity: apiSpace.capacity,
            digitalLockId: apiSpace.digitalLockId,
            accessCodeEnabled: apiSpace.accessCodeEnabled || false,
            createdAt: apiSpace.createdAt || '',
            updatedAt: apiSpace.updatedAt || ''
        };
    }

    getSpaces(type?: string, available?: boolean, startDate?: string, endDate?: string, search?: string, page?: number, size?: number): Observable<PaginatedSpacesResponse> {
        return this.spacesApi.getSpaces(type as any, available, startDate, endDate, search, page, size).pipe(
            map(response => ({
                content: (response.content || []).map(this.fromApiSpace),
                totalElements: response.totalElements || 0,
                totalPages: response.totalPages || 0,
                size: response.size || 0,
                number: response.number || 0,
                first: response.first || false,
                last: response.last || false,
                numberOfElements: response.numberOfElements || 0
            }))
        );
    }

    getSpaceById(id: string): Observable<UISpace | undefined> {
        return this.spacesApi.getSpace(id).pipe(
            map(space => space ? this.fromApiSpace(space) : undefined)
        );
    }

    getSpaceAvailability(spaceId: string, startDate: string, endDate: string): Observable<AvailabilityResponse> {
        return this.spacesApi.getSpaceAvailability(spaceId, startDate, endDate).pipe(
            map(response => ({
                available: response.available || false,
                conflicts: response.conflicts || [],
                quota: response.quota,
                price: response.price,
                currency: response.currency
            }))
        );
    }

    getSharedSpaceReservations(spaceId: string, startDate: string, endDate: string): Observable<any[]> {
        return this.spacesApi.getSharedSpaceReservations(spaceId, startDate, endDate);
    }

    // Cache methods
    loadSpacesByIds(spaceIds: string[]): Observable<Map<string, UISpace>> {
        if (spaceIds.length === 0) {
            return of(new Map());
        }

        const uniqueSpaceIds = [...new Set(spaceIds)];
        const cached = new Map<string, UISpace>();
        const missingIds: string[] = [];

        // Check cache
        uniqueSpaceIds.forEach(id => {
            const cachedSpace = this.cache().get(id);
            if (cachedSpace) {
                cached.set(id, cachedSpace);
            } else {
                missingIds.push(id);
            }
        });

        // If all spaces are cached, return immediately
        if (missingIds.length === 0) {
            const result = new Map<string, UISpace>();
            uniqueSpaceIds.forEach(id => {
                const space = this.cache().get(id);
                if (space) {
                    result.set(id, space);
                }
            });
            return of(result);
        }

        // Load all spaces in one request (they will be cached)
        return this.loadAllSpaces().pipe(
            map(() => {
                // Return cache with requested spaces
                const result = new Map<string, UISpace>();
                uniqueSpaceIds.forEach(id => {
                    const space = this.cache().get(id);
                    if (space) {
                        result.set(id, space);
                    }
                });
                return result;
            })
        );
    }

    getCachedSpace(id: string): UISpace | undefined {
        return this.cache().get(id);
    }

    clearSpacesCache(): void {
        this.cache.set(new Map());
        this.allSpacesLoaded.set(false);
        this.loadingPromise = null;
    }

    preloadAllSpaces(): Observable<void> {
        return this.loadAllSpaces();
    }

    private loadAllSpaces(): Observable<void> {
        if (this.allSpacesLoaded()) {
            return of(void 0);
        }

        // If already loading, return the same observable
        if (this.loadingPromise) {
            return this.loadingPromise;
        }

        // Load all spaces (up to 1000, which should be enough)
        this.loadingPromise = this.getSpaces(undefined, undefined, undefined, undefined, undefined, 0, 1000).pipe(
            map((response: PaginatedSpacesResponse) => {
                const cacheMap = new Map<string, UISpace>();
                response.content.forEach((space: UISpace) => {
                    cacheMap.set(space.id, space);
                });

                // Merge with existing cache
                const existingCache = this.cache();
                cacheMap.forEach((space, id) => {
                    existingCache.set(id, space);
                });

                this.cache.set(existingCache);
                this.allSpacesLoaded.set(true);
                this.loadingPromise = null; // Reset loading promise after completion
            }),
            shareReplay(1)
        ) as Observable<void>;

        return this.loadingPromise;
    }
}
