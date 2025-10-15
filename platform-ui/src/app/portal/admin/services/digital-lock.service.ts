import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { UIAccessCode, UIDigitalLock } from '../../../models/UIDigitalLock';

export interface AdminDigitalLockService {
    getDigitalLocks(page?: number, size?: number): Observable<UIDigitalLock[]>;
    getDigitalLockById(id: string): Observable<UIDigitalLock>;
    getDigitalLocksByType(type: string): Observable<UIDigitalLock[]>;
    getDigitalLocksByStatus(status: string): Observable<UIDigitalLock[]>;
    createDigitalLock(lock: Partial<UIDigitalLock>): Observable<UIDigitalLock>;
    updateDigitalLock(id: string, lock: Partial<UIDigitalLock>): Observable<UIDigitalLock>;
    deleteDigitalLock(id: string): Observable<void>;
    toggleDigitalLockStatus(id: string): Observable<UIDigitalLock>;
    generateAccessCode(lockId: string, duration: number, reason: string): Observable<UIAccessCode>;
}

export const DIGITAL_LOCK_SERVICE_TOKEN = new InjectionToken<AdminDigitalLockService>('AdminDigitalLockService');
