import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { DigitalLocksLoader } from '../../../../mock/digital-locks-loader';
import { UIAccessCode, UIDigitalLock } from '../../../../models/UIDigitalLock';
import { AdminDigitalLockService } from '../digital-lock.service';

@Injectable({
    providedIn: 'root',
})
export class MockAdminDigitalLockService implements AdminDigitalLockService {
    private digitalLocksLoader = inject(DigitalLocksLoader);

    getDigitalLocks(page: number = 0, size: number = 100): Observable<UIDigitalLock[]> {
        return this.digitalLocksLoader.getAllDigitalLocks();
    }

    getDigitalLockById(id: string): Observable<UIDigitalLock> {
        return this.digitalLocksLoader.getDigitalLockById(id).pipe(
            map(lock => {
                if (!lock) {
                    throw new Error(`DigitalLock with id ${id} not found`);
                }
                return lock;
            })
        );
    }

    getDigitalLocksByType(type: string): Observable<UIDigitalLock[]> {
        return this.digitalLocksLoader.getDigitalLocksByType(type);
    }

    getDigitalLocksByStatus(status: string): Observable<UIDigitalLock[]> {
        return this.digitalLocksLoader.getDigitalLocksByStatus(status);
    }

    createDigitalLock(lock: Partial<UIDigitalLock>): Observable<UIDigitalLock> {
        const newLock: UIDigitalLock = {
            id: "0",
            name: lock.name || '',
            type: lock.type || 'TTLOCK',
            status: lock.status || 'ACTIVE',
            ttlockConfig: lock.ttlockConfig || null,
            nukiConfig: lock.nukiConfig || null,
            createdAt: '',
            updatedAt: ''
        };
        return this.digitalLocksLoader.addDigitalLock(newLock);
    }

    updateDigitalLock(id: string, lock: Partial<UIDigitalLock>): Observable<UIDigitalLock> {
        return this.digitalLocksLoader.updateDigitalLock(id, lock).pipe(
            map(updatedLock => {
                if (!updatedLock) {
                    throw new Error(`DigitalLock with id ${id} not found`);
                }
                return updatedLock;
            })
        );
    }

    deleteDigitalLock(id: string): Observable<void> {
        return this.digitalLocksLoader.deleteDigitalLock(id);
    }

    toggleDigitalLockStatus(id: string): Observable<UIDigitalLock> {
        return this.getDigitalLockById(id).pipe(
            map(lock => {
                const newStatus: 'ACTIVE' | 'INACTIVE' | 'ERROR' = lock.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
                return { ...lock, status: newStatus };
            }),
            map(updatedLock => {
                this.digitalLocksLoader.updateDigitalLock(id, updatedLock);
                return updatedLock;
            })
        );
    }

    generateAccessCode(lockId: string, duration: number, reason: string): Observable<UIAccessCode> {
        // Mock implementation
        const code = Math.random().toString(36).substring(2, 8).toUpperCase();
        const now = new Date();
        const expiresAt = new Date(now.getTime() + duration * 60 * 60 * 1000);

        const accessCode: UIAccessCode = {
            code,
            generatedAt: now.toISOString(),
            expiresAt: expiresAt.toISOString(),
            isActive: true
        };

        return new Observable(observer => {
            setTimeout(() => {
                observer.next(accessCode);
                observer.complete();
            }, 500);
        });
    }
}
