import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { PaginatedUnits } from '../../../../api-client/model/paginatedUnits';
import { Unit } from '../../../../api-client/model/unit';
import { loadUnitsData } from '../../../../mock/units-loader';
import { UnitsService } from '../units.service';

@Injectable({
    providedIn: 'root',
})
export class MockUnitsService implements UnitsService {
    private index = 1;
    public allUnits: Unit[] = [];

    constructor() {
        this.allUnits = loadUnitsData();
    }

    getUnits(page?: number, size?: number, search?: string): Observable<PaginatedUnits> {
        let filteredUnits = [...this.allUnits] as Unit[];

        // Apply search filter
        if (search && search.trim()) {
            const searchLower = search.toLowerCase().trim();
            filteredUnits = filteredUnits.filter(unit =>
                unit.name?.toLowerCase().includes(searchLower)
            );
        }

        // Apply pagination
        const currentPage = page ?? 0;
        const pageSize = size ?? 10;
        const startIndex = currentPage * pageSize;
        const endIndex = startIndex + pageSize;
        const paginatedContent = filteredUnits.slice(startIndex, endIndex);
        const totalElements = filteredUnits.length;
        const totalPages = Math.ceil(totalElements / pageSize);

        const result: PaginatedUnits = {
            content: paginatedContent,
            totalElements: totalElements,
            totalPages: totalPages,
            size: pageSize,
            number: currentPage,
            first: currentPage === 0,
            last: currentPage >= totalPages - 1,
            numberOfElements: paginatedContent.length,
        };

        return of(result).pipe(delay(300));
    }

    getUnit(id: string): Observable<Unit> {
        const unit = this.allUnits.find((u) => u.id === id) as Unit;
        return of(unit || ({} as Unit)).pipe(delay(200));
    }

    createUnit(name: string): Observable<Unit> {
        const newUnit: Unit = {
            id: `mock-${this.index++}`,
            name: name,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            members: [],
        };
        this.allUnits.push(newUnit as any);
        return of(newUnit).pipe(delay(300));
    }

    updateUnit(id: string, name: string): Observable<Unit> {
        const unitIndex = this.allUnits.findIndex((u) => u.id === id);
        if (unitIndex >= 0) {
            const updatedUnit = {
                ...this.allUnits[unitIndex],
                name: name,
                updatedAt: new Date().toISOString(),
            };
            this.allUnits[unitIndex] = updatedUnit as any;
            return of(updatedUnit as Unit).pipe(delay(300));
        }
        return of({} as Unit).pipe(delay(300));
    }

    deleteUnit(id: string): Observable<void> {
        const unitIndex = this.allUnits.findIndex((u) => u.id === id);
        if (unitIndex >= 0) {
            this.allUnits.splice(unitIndex, 1);
            console.log(`Unit deleted: ${id}`);
        } else {
            console.error(`Unit not found: ${id}`);
        }
        return of(undefined).pipe(delay(300));
    }
}

