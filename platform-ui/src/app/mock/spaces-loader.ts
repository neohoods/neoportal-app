import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { Space } from '../portal/spaces/services/spaces.service';
import spacesData from './spaces.json';

@Injectable({
    providedIn: 'root'
})
export class SpacesLoader {
    private spaces: Space[] = [];
    private spacesLoaded = false;

    constructor() { }

    private loadSpaces(): Space[] {
        if (this.spacesLoaded) {
            return this.spaces;
        }

        this.spaces = spacesData as Space[];
        this.spacesLoaded = true;
        return this.spaces;
    }

    getAllSpaces(): Observable<Space[]> {
        return of(this.loadSpaces()).pipe(delay(500));
    }

    getSpaceById(id: string): Observable<Space | undefined> {
        const spaces = this.loadSpaces();
        const space = spaces.find(s => s.id === id);
        return of(space).pipe(delay(300));
    }

    getSpacesByType(type: string): Observable<Space[]> {
        const spaces = this.loadSpaces();
        const filteredSpaces = spaces.filter(s => s.type === type);
        return of(filteredSpaces).pipe(delay(300));
    }

    getAvailableSpaces(): Observable<Space[]> {
        const spaces = this.loadSpaces();
        const availableSpaces = spaces.filter(s => s.status === 'ACTIVE');
        return of(availableSpaces).pipe(delay(300));
    }

    updateSpace(id: string, updatedSpace: Partial<Space>): Observable<Space | undefined> {
        const spaces = this.loadSpaces();
        const index = spaces.findIndex(s => s.id === id);

        if (index === -1) {
            return of(undefined).pipe(delay(300));
        }

        // Merge the updated data with existing space
        this.spaces[index] = {
            ...this.spaces[index],
            ...updatedSpace,
            id: this.spaces[index].id, // Keep original ID
            updatedAt: new Date().toISOString()
        };

        return of(this.spaces[index]).pipe(delay(300));
    }

    addSpace(newSpace: Space): Observable<Space> {
        const spaces = this.loadSpaces();
        this.spaces.push(newSpace);
        return of(newSpace).pipe(delay(300));
    }
}
