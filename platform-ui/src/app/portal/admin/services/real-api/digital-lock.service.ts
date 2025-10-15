import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { DigitalLock, DigitalLockAdminApiService, DigitalLockStatus, DigitalLockType, GenerateDigitalLockAccessCodeRequest, UpdateDigitalLockStatusRequest } from '../../../../api-client';
import { UIAccessCode, UIDigitalLock } from '../../../../models/UIDigitalLock';
import { AdminDigitalLockService } from '../digital-lock.service';

@Injectable({
    providedIn: 'root',
})
export class RealApiAdminDigitalLockService implements AdminDigitalLockService {
    constructor(private digitalLockAdminApi: DigitalLockAdminApiService) { }

    private fromApiDigitalLock(apiLock: DigitalLock): UIDigitalLock {
        return {
            id: apiLock.id,
            name: apiLock.name,
            type: apiLock.type as UIDigitalLock['type'],
            status: apiLock.status as UIDigitalLock['status'],
            ttlockConfig: apiLock.ttlockConfig ? {
                id: apiLock.ttlockConfig.id || '0',
                deviceId: apiLock.ttlockConfig.deviceId || '',
                location: apiLock.ttlockConfig.location || '',
                batteryLevel: apiLock.ttlockConfig.batteryLevel,
                signalStrength: apiLock.ttlockConfig.signalStrength,
                lastSeen: apiLock.ttlockConfig.lastSeen
            } : null,
            nukiConfig: apiLock.nukiConfig ? {
                id: apiLock.nukiConfig.id || '0',
                deviceId: apiLock.nukiConfig.deviceId || '',
                token: apiLock.nukiConfig.token || '',
                batteryLevel: apiLock.nukiConfig.batteryLevel,
                lastSeen: apiLock.nukiConfig.lastSeen
            } : null,
            createdAt: apiLock.createdAt,
            updatedAt: apiLock.updatedAt,
        };
    }

    private toApiDigitalLock(uiLock: Partial<UIDigitalLock>): DigitalLock {
        return {
            id: uiLock.id || '0',
            name: uiLock.name || '',
            type: uiLock.type as DigitalLockType,
            status: uiLock.status as DigitalLockStatus,
            ttlockConfig: uiLock.ttlockConfig ? {
                id: uiLock.ttlockConfig.id,
                deviceId: uiLock.ttlockConfig.deviceId,
                location: uiLock.ttlockConfig.location,
                batteryLevel: uiLock.ttlockConfig.batteryLevel,
                signalStrength: uiLock.ttlockConfig.signalStrength,
                lastSeen: uiLock.ttlockConfig.lastSeen
            } : undefined,
            nukiConfig: uiLock.nukiConfig ? {
                id: uiLock.nukiConfig.id,
                deviceId: uiLock.nukiConfig.deviceId,
                token: uiLock.nukiConfig.token,
                batteryLevel: uiLock.nukiConfig.batteryLevel,
                lastSeen: uiLock.nukiConfig.lastSeen
            } : undefined,
            createdAt: uiLock.createdAt || new Date().toISOString(),
            updatedAt: uiLock.updatedAt || new Date().toISOString(),
        };
    }

    getDigitalLocks(page?: number, size?: number): Observable<UIDigitalLock[]> {
        return this.digitalLockAdminApi.getDigitalLocks(page, size).pipe(
            map(apiLocks => apiLocks.map(this.fromApiDigitalLock))
        );
    }

    getDigitalLockById(id: string): Observable<UIDigitalLock> {
        return this.digitalLockAdminApi.getDigitalLock(id).pipe(
            map(this.fromApiDigitalLock)
        );
    }

    getDigitalLocksByType(type: string): Observable<UIDigitalLock[]> {
        return this.digitalLockAdminApi.getDigitalLocks(0, 1000, undefined, type as DigitalLockType).pipe(
            map(apiLocks => apiLocks.map(this.fromApiDigitalLock))
        );
    }

    getDigitalLocksByStatus(status: string): Observable<UIDigitalLock[]> {
        return this.digitalLockAdminApi.getDigitalLocks(0, 1000, undefined, undefined, status as DigitalLockStatus).pipe(
            map(apiLocks => apiLocks.map(this.fromApiDigitalLock))
        );
    }

    createDigitalLock(lock: Partial<UIDigitalLock>): Observable<UIDigitalLock> {
        const apiLock = this.toApiDigitalLock(lock);
        return this.digitalLockAdminApi.createDigitalLock(apiLock).pipe(
            map(this.fromApiDigitalLock)
        );
    }

    updateDigitalLock(id: string, lock: Partial<UIDigitalLock>): Observable<UIDigitalLock> {
        const apiLock = this.toApiDigitalLock(lock);
        return this.digitalLockAdminApi.updateDigitalLock(id, apiLock).pipe(
            map(this.fromApiDigitalLock)
        );
    }

    deleteDigitalLock(id: string): Observable<void> {
        return this.digitalLockAdminApi.deleteDigitalLock(id);
    }

    toggleDigitalLockStatus(id: string): Observable<UIDigitalLock> {
        return this.getDigitalLockById(id).pipe(
            switchMap(lock => {
                const newStatus = lock.status === 'ACTIVE' ? DigitalLockStatus.Inactive : DigitalLockStatus.Active;
                const updateRequest: UpdateDigitalLockStatusRequest = { status: newStatus };
                return this.digitalLockAdminApi.updateDigitalLockStatus(id, updateRequest).pipe(
                    map(this.fromApiDigitalLock)
                );
            })
        );
    }

    generateAccessCode(lockId: string, duration: number, reason: string): Observable<UIAccessCode> {
        const request: GenerateDigitalLockAccessCodeRequest = { duration, reason };
        return this.digitalLockAdminApi.generateDigitalLockAccessCode(lockId, request).pipe(
            map(apiAccessCode => ({
                code: apiAccessCode.code,
                generatedAt: apiAccessCode.generatedAt,
                expiresAt: apiAccessCode.expiresAt,
                isActive: apiAccessCode.isActive || false,
            }))
        );
    }
}
