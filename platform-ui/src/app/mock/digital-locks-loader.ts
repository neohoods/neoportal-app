import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { UIDigitalLock } from '../models/UIDigitalLock';
import digitalLocksData from './digital-locks.json';

@Injectable({
    providedIn: 'root'
})
export class DigitalLocksLoader {
    private locks: UIDigitalLock[] = [];
    private locksLoaded = false;

    constructor() { }

    private loadLocks(): UIDigitalLock[] {
        if (this.locksLoaded) {
            return this.locks;
        }

        this.locks = digitalLocksData as UIDigitalLock[];
        this.locksLoaded = true;
        return this.locks;
    }

    getAllDigitalLocks(): Observable<UIDigitalLock[]> {
        return of(this.loadLocks()).pipe(delay(500));
    }

    getDigitalLockById(id: string): Observable<UIDigitalLock | undefined> {
        const locks = this.loadLocks();
        const lock = locks.find(l => l.id === id);
        return of(lock).pipe(delay(300));
    }

    getDigitalLocksByType(type: string): Observable<UIDigitalLock[]> {
        const locks = this.loadLocks();
        const filteredLocks = locks.filter(l => l.type === type);
        return of(filteredLocks).pipe(delay(300));
    }

    getDigitalLocksByStatus(status: string): Observable<UIDigitalLock[]> {
        const locks = this.loadLocks();
        const filteredLocks = locks.filter(l => l.status === status);
        return of(filteredLocks).pipe(delay(300));
    }

    updateDigitalLock(id: string, updatedLock: Partial<UIDigitalLock>): Observable<UIDigitalLock | undefined> {
        const locks = this.loadLocks();
        const index = locks.findIndex(l => l.id === id);

        if (index === -1) {
            return of(undefined).pipe(delay(300));
        }

        this.locks[index] = {
            ...this.locks[index],
            ...updatedLock,
            id: this.locks[index].id,
            updatedAt: new Date().toISOString()
        };

        return of(this.locks[index]).pipe(delay(300));
    }

    addDigitalLock(newLock: UIDigitalLock): Observable<UIDigitalLock> {
        const locks = this.loadLocks();
        const newId = (Math.max(...locks.map(l => Number(l.id)), 0) + 1).toString();
        const lockToAdd = {
            ...newLock,
            id: newId,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString()
        };
        this.locks.push(lockToAdd);
        return of(lockToAdd).pipe(delay(300));
    }

    deleteDigitalLock(id: string): Observable<void> {
        const locks = this.loadLocks();
        const index = locks.findIndex(l => l.id === id);
        if (index !== -1) {
            this.locks.splice(index, 1);
        }
        return of(undefined).pipe(delay(300));
    }
}

