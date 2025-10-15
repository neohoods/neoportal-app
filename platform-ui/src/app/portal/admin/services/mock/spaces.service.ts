import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay, map } from 'rxjs/operators';
import { SpacesLoader } from '../../../../mock/spaces-loader';
import { UIDigitalLock } from '../../../../models/UIDigitalLock';
import { fromApiSpace, toApiSpace, UISpace } from '../../../../models/UISpace';
import { AdminSpacesService } from '../spaces.service';

@Injectable({
    providedIn: 'root',
})
export class MockAdminSpacesService implements AdminSpacesService {
    constructor(private spacesLoader: SpacesLoader) { }

    getSpaces(page: number = 0, size: number = 10, type?: string, status?: string, search?: string): Observable<{ content: UISpace[], totalElements: number, totalPages: number, size: number, number: number, first: boolean, last: boolean, numberOfElements: number }> {
        return this.spacesLoader.getAllSpaces().pipe(
            delay(300),
            (source) => {
                return new Observable(observer => {
                    source.subscribe({
                        next: (spaces) => {
                            // Apply filters
                            let filteredSpaces = spaces;

                            if (type) {
                                filteredSpaces = filteredSpaces.filter(space => space.type === type);
                            }

                            if (status) {
                                filteredSpaces = filteredSpaces.filter(space => space.status === status);
                            }

                            if (search) {
                                const searchLower = search.toLowerCase();
                                filteredSpaces = filteredSpaces.filter(space =>
                                    space.name.toLowerCase().includes(searchLower) ||
                                    space.description?.toLowerCase().includes(searchLower)
                                );
                            }

                            // Apply pagination
                            const totalElements = filteredSpaces.length;
                            const totalPages = Math.ceil(totalElements / size);
                            const startIndex = page * size;
                            const endIndex = startIndex + size;
                            const content = filteredSpaces.slice(startIndex, endIndex);

                            const result = {
                                content: content,
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

    getSpace(id: string): Observable<UISpace> {
        return this.spacesLoader.getSpaceById(id).pipe(
            delay(300),
            map(space => {
                if (!space) {
                    throw new Error('Space not found');
                }
                return space;
            })
        );
    }

    createSpace(space: Partial<UISpace>): Observable<UISpace> {
        const apiSpace = toApiSpace(space);
        const newSpace = {
            id: `space-${Date.now()}`,
            name: space.name || 'New Space',
            description: space.description || '',
            instructions: space.instructions || '',
            type: space.type || 'GUEST_ROOM' as any,
            status: space.status || 'ACTIVE' as any,
            images: space.images || [],
            pricing: space.pricing || { tenantPrice: 0, ownerPrice: 0, currency: 'EUR' },
            quota: space.quota || { used: 0, max: 0, period: 'YEAR', resetDate: '2024-01-01' },
            rules: space.rules || {
                minDurationDays: 1,
                maxDurationDays: 30,
                maxReservationsPerYear: 12,
                allowedDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'],
                allowedHours: { start: '08:00', end: '22:00' },
                cleaningDays: [],
                requiresApartmentAccess: false,
                conflictWithTypes: []
            },
            accessCodeEnabled: space.accessCodeEnabled || false,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString()
        };

        return this.spacesLoader.addSpace(newSpace as any).pipe(
            delay(500),
            map((space: any) => fromApiSpace(space))
        );
    }

    updateSpace(id: string, space: Partial<UISpace>): Observable<UISpace> {
        const apiSpace = toApiSpace(space);

        return this.spacesLoader.updateSpace(id, apiSpace as any).pipe(
            delay(500),
            map(updatedSpace => {
                if (!updatedSpace) {
                    throw new Error('Space not found');
                }
                return updatedSpace;
            })
        );
    }

    deleteSpace(id: string): Observable<void> {
        // Mock implementation - in real app this would call the API
        console.log(`Deleting space: ${id}`);
        return of(undefined).pipe(delay(500));
    }

    toggleSpaceStatus(id: string): Observable<UISpace> {
        return this.spacesLoader.getSpaceById(id).pipe(
            delay(500),
            map(space => {
                if (!space) {
                    throw new Error('Space not found');
                }
                const updatedSpace = {
                    ...space,
                    status: space.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
                    updatedAt: new Date().toISOString()
                } as UISpace;
                return updatedSpace;
            })
        );
    }

    getSpaceDigitalLock(spaceId: string): Observable<UIDigitalLock | null> {
        // Mock implementation - return associated digital lock for a space
        const mockAssociations: { [key: string]: UIDigitalLock | null } = {
            'space-1': {
                id: "1",
                name: 'Entrée principale',
                type: 'TTLOCK',
                status: 'ACTIVE',
                ttlockConfig: {
                    id: "1",
                    deviceId: 'TTL-001',
                    location: 'Rez-de-chaussée',
                    batteryLevel: 85,
                    signalStrength: -45,
                    lastSeen: '2024-01-15T10:30:00Z'
                },
                nukiConfig: null,
                createdAt: '2024-01-01T00:00:00Z',
                updatedAt: '2024-01-15T10:30:00Z'
            },
            'space-2': {
                id: "2",
                name: 'Salle commune',
                type: 'TTLOCK',
                status: 'ACTIVE',
                ttlockConfig: {
                    id: "2",
                    deviceId: 'TTL-002',
                    location: 'Rez-de-chaussée',
                    batteryLevel: 92,
                    signalStrength: -38,
                    lastSeen: '2024-01-15T10:25:00Z'
                },
                nukiConfig: null,
                createdAt: '2024-01-01T00:00:00Z',
                updatedAt: '2024-01-15T10:25:00Z'
            }
        };

        return of(mockAssociations[spaceId] || null).pipe(delay(300));
    }

    associateSpaceWithDigitalLock(spaceId: string, digitalLockId: string | null): Observable<void> {
        // Mock implementation - just log and return success
        console.log(`Associating space ${spaceId} with digital lock:`, digitalLockId);
        return of(undefined).pipe(delay(500));
    }
}
