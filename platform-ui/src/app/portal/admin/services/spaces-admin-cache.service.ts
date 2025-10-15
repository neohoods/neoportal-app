import { inject, Injectable, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { map, shareReplay } from 'rxjs/operators';
import { SpacesAdminApiService } from '../../../api-client/api/spacesAdminApi.service';
import { PaginatedSpaces } from '../../../api-client/model/paginatedSpaces';
import { Space } from '../../../api-client/model/space';

/**
 * Cache service for admin spaces to avoid multiple requests for the same spaces
 * Used in admin reservations components to optimize space loading
 * Loads all spaces in one request and caches them in memory
 */
@Injectable({
    providedIn: 'root'
})
export class SpacesAdminCacheService {
    private cache = signal<Map<string, Space>>(new Map());
    private loadingPromise: Observable<void> | null = null;
    private allSpacesLoaded = signal(false);
    private spacesAdminApi = inject(SpacesAdminApiService);

    /**
     * Load spaces by IDs, using cache when possible
     * Makes a single request to get all spaces if not already cached
     */
    loadSpacesByIds(spaceIds: string[]): Observable<Map<string, Space>> {
        if (spaceIds.length === 0) {
            return of(new Map());
        }

        const uniqueSpaceIds = [...new Set(spaceIds)];
        const cached = new Map<string, Space>();
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
            const result = new Map<string, Space>();
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
                const result = new Map<string, Space>();
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

    /**
     * Load all spaces in one request and cache them
     * Uses shareReplay to ensure only one request is made even if called multiple times
     */
    private loadAllSpaces(): Observable<void> {
        if (this.allSpacesLoaded()) {
            return of(void 0);
        }

        // If already loading, return the same observable
        if (this.loadingPromise) {
            return this.loadingPromise;
        }

        // Load all spaces (up to 1000, which should be enough)
        this.loadingPromise = this.spacesAdminApi.getAdminSpaces(undefined, undefined, undefined, 0, 1000).pipe(
            map((response: PaginatedSpaces) => {
                const cacheMap = new Map<string, Space>();
                const allSpaces = response.content || [];
                allSpaces.forEach((space: Space) => {
                    if (space.id) {
                        cacheMap.set(space.id, space);
                    }
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

    /**
     * Get a space from cache
     */
    getSpace(id: string): Space | undefined {
        return this.cache().get(id);
    }

    /**
     * Clear cache (useful for refresh)
     */
    clearCache(): void {
        this.cache.set(new Map());
        this.allSpacesLoaded.set(false);
        this.loadingPromise = null;
    }

    /**
     * Preload all spaces (useful for initial load)
     */
    preloadAllSpaces(): Observable<void> {
        return this.loadAllSpaces();
    }
}

