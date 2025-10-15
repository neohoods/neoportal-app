import { Injectable } from '@angular/core';
import { Observable, map, of, switchMap } from 'rxjs';
import { DigitalLockAdminApiService } from '../../../../api-client/api/digitalLockAdminApi.service';
import { SpacesAdminApiService } from '../../../../api-client/api/spacesAdminApi.service';
import { DigitalLock } from '../../../../api-client/model/digitalLock';
import { UIDigitalLock } from '../../../../models/UIDigitalLock';
import { UISpace, fromApiSpace, toApiSpace } from '../../../../models/UISpace';
import { AdminSpacesService } from '../spaces.service';

@Injectable({
    providedIn: 'root',
})
export class RealApiAdminSpacesService implements AdminSpacesService {
    constructor(
        private spacesAdminApi: SpacesAdminApiService,
        private digitalLockAdminApi: DigitalLockAdminApiService
    ) { }

    private mapDigitalLockToUI(apiLock: DigitalLock): UIDigitalLock {
        return {
            id: apiLock.id,
            name: apiLock.name,
            type: apiLock.type as 'TTLOCK' | 'NUKI' | 'YALE',
            status: apiLock.status as 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE' | 'ERROR',
            batteryLevel: apiLock.ttlockConfig?.batteryLevel || apiLock.nukiConfig?.batteryLevel,
            lastSeen: apiLock.ttlockConfig?.lastSeen || apiLock.nukiConfig?.lastSeen,
            ttlockConfig: apiLock.ttlockConfig ? {
                id: apiLock.ttlockConfig.id || '',
                deviceId: apiLock.ttlockConfig.deviceId || '',
                location: apiLock.ttlockConfig.location || '',
                batteryLevel: apiLock.ttlockConfig.batteryLevel,
                signalStrength: apiLock.ttlockConfig.signalStrength,
                lastSeen: apiLock.ttlockConfig.lastSeen
            } : null,
            nukiConfig: apiLock.nukiConfig ? {
                id: apiLock.nukiConfig.id || '',
                deviceId: apiLock.nukiConfig.deviceId || '',
                token: apiLock.nukiConfig.token || '',
                batteryLevel: apiLock.nukiConfig.batteryLevel,
                lastSeen: apiLock.nukiConfig.lastSeen
            } : null,
            createdAt: apiLock.createdAt,
            updatedAt: apiLock.updatedAt
        };
    }

    getSpaces(page: number = 0, size: number = 10, type?: string, status?: string, search?: string): Observable<{ content: UISpace[], totalElements: number, totalPages: number, size: number, number: number, first: boolean, last: boolean, numberOfElements: number }> {
        return this.spacesAdminApi.getAdminSpaces(type as any, status as any, search, page, size).pipe(
            map(response => ({
                ...response,
                content: response.content?.map(fromApiSpace) || []
            }))
        );
    }

    getSpace(id: string): Observable<UISpace> {
        return this.spacesAdminApi.getAdminSpace(id).pipe(
            map(fromApiSpace)
        );
    }

    createSpace(space: Partial<UISpace>): Observable<UISpace> {
        const apiSpace = toApiSpace(space);
        const spaceRequest = {
            name: apiSpace.name!,
            description: apiSpace.description || '',
            instructions: apiSpace.instructions || '',
            type: apiSpace.type!,
            status: apiSpace.status!,
            pricing: apiSpace.pricing!,
            rules: apiSpace.rules!,
            images: apiSpace.images || [],
            quota: apiSpace.quota,
            digitalLockId: apiSpace.digitalLockId,
            accessCodeEnabled: apiSpace.accessCodeEnabled
        };
        return this.spacesAdminApi.createSpace(spaceRequest).pipe(
            map(fromApiSpace)
        );
    }

    updateSpace(id: string, space: Partial<UISpace>): Observable<UISpace> {
        const apiSpace = toApiSpace(space);
        const spaceRequest = {
            name: apiSpace.name!,
            description: apiSpace.description || '',
            instructions: apiSpace.instructions || '',
            type: apiSpace.type!,
            status: apiSpace.status!,
            pricing: apiSpace.pricing!,
            rules: apiSpace.rules!,
            images: apiSpace.images || [],
            quota: apiSpace.quota,
            digitalLockId: apiSpace.digitalLockId,
            accessCodeEnabled: apiSpace.accessCodeEnabled
        };
        return this.spacesAdminApi.updateSpace(id, spaceRequest).pipe(
            map(fromApiSpace)
        );
    }

    deleteSpace(id: string): Observable<void> {
        return this.spacesAdminApi.deleteSpace(id);
    }

    toggleSpaceStatus(id: string): Observable<UISpace> {
        // This method doesn't exist in the API, so we'll implement it by getting the space and updating its status
        return this.spacesAdminApi.getAdminSpace(id).pipe(
            (source) => {
                return new Observable(observer => {
                    source.subscribe({
                        next: (space) => {
                            const uiSpace = fromApiSpace(space);
                            const newStatus = uiSpace.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
                            this.updateSpace(uiSpace.id, { ...uiSpace, status: newStatus as any }).subscribe({
                                next: (updatedSpace) => {
                                    observer.next(updatedSpace);
                                    observer.complete();
                                },
                                error: (error: any) => observer.error(error)
                            });
                        },
                        error: (error: any) => observer.error(error)
                    });
                });
            }
        );
    }

    getSpaceDigitalLock(spaceId: string): Observable<UIDigitalLock | null> {
        // First get the space to find the digitalLockId
        return this.spacesAdminApi.getAdminSpace(spaceId).pipe(
            switchMap(space => {
                if (!space.digitalLockId) {
                    return of(null);
                }
                // Get the digital lock details
                return this.digitalLockAdminApi.getDigitalLock(space.digitalLockId).pipe(
                    map(lock => this.mapDigitalLockToUI(lock))
                );
            })
        );
    }

    associateSpaceWithDigitalLock(spaceId: string, digitalLockId: string | null): Observable<void> {
        if (!digitalLockId) {
            // Remove the digital lock association by updating the space with null digitalLockId
            return this.spacesAdminApi.getAdminSpace(spaceId).pipe(
                switchMap(space => {
                    const spaceRequest = {
                        name: space.name,
                        description: space.description,
                        instructions: space.instructions,
                        type: space.type,
                        status: space.status,
                        pricing: space.pricing,
                        rules: space.rules,
                        images: space.images || [],
                        quota: space.quota,
                        digitalLockId: undefined,
                        accessCodeEnabled: space.accessCodeEnabled
                    };
                    return this.spacesAdminApi.updateSpace(spaceId, spaceRequest);
                }),
                map(() => undefined)
            );
        }

        // Associate the space with the digital lock
        return this.spacesAdminApi.getAdminSpace(spaceId).pipe(
            switchMap(space => {
                const spaceRequest = {
                    name: space.name,
                    description: space.description,
                    instructions: space.instructions,
                    type: space.type,
                    status: space.status,
                    pricing: space.pricing,
                    rules: space.rules,
                    images: space.images || [],
                    quota: space.quota,
                    digitalLockId: digitalLockId,
                    accessCodeEnabled: space.accessCodeEnabled
                };
                return this.spacesAdminApi.updateSpace(spaceId, spaceRequest);
            }),
            map(() => undefined)
        );
    }
}
