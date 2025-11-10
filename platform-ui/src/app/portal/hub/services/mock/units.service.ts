import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
// Types will be generated from OpenAPI - using temporary types for now
// import { Unit } from '../../../../../api-client/model/unit';
// import { PaginatedUnits } from '../../../../../api-client/model/paginatedUnits';
// import { UnitMember } from '../../../../../api-client/model/unitMember';
// import { UnitType } from '../../../../../api-client/model/unitType';
// import { UnitMemberResidenceRole } from '../../../../../api-client/model/unitMemberResidenceRole';
import { UnitsService } from '../units.service';

// Temporary types until OpenAPI models are regenerated
type Unit = any;
type PaginatedUnits = any;
type UnitMember = any;
type UnitType = 'FLAT' | 'GARAGE' | 'PARKING' | null;
type UnitMemberResidenceRole = 'PROPRIETAIRE' | 'BAILLEUR' | 'MANAGER' | 'TENANT' | null;

@Injectable({
  providedIn: 'root',
})
export class MockUnitsService implements UnitsService {
  private mockUnits: Unit[] = [];

  getUnitsDirectory(
    page?: number,
    size?: number,
    type?: UnitType,
    search?: string,
    userId?: string
  ): Observable<PaginatedUnits> {
    let filtered = [...this.mockUnits];

    if (type) {
      filtered = filtered.filter((u) => u.type === type);
    }

    if (search) {
      const searchLower = search.toLowerCase();
      filtered = filtered.filter((u) => u.name?.toLowerCase().includes(searchLower));
    }

    if (userId) {
      filtered = filtered.filter((u: Unit) =>
        u.members?.some((m: any) => m.userId === userId)
      );
    }

    const currentPage = page ?? 0;
    const pageSize = size ?? 20;
    const start = currentPage * pageSize;
    const end = start + pageSize;
    const paginated = filtered.slice(start, end);

    return of({
      content: paginated,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / pageSize),
      number: currentPage,
      size: pageSize,
      first: currentPage === 0,
      last: currentPage >= Math.ceil(filtered.length / pageSize) - 1,
      numberOfElements: paginated.length,
    } as PaginatedUnits).pipe(delay(300));
  }

  getRelatedParkingGarages(userId: string): Observable<Unit[]> {
    return of([]).pipe(delay(300));
  }

  updateMemberResidenceRole(
    unitId: string,
    userId: string,
    residenceRole: UnitMemberResidenceRole | null
  ): Observable<UnitMember> {
    // Mock implementation
    return of({} as UnitMember).pipe(delay(300));
  }

  createJoinRequest(unitId: string, message?: string): Observable<any> {
    return of({ id: 'mock-request-id', unitId, status: 'PENDING' }).pipe(delay(300));
  }

  getJoinRequests(unitId: string): Observable<any[]> {
    return of([]).pipe(delay(300));
  }

  getMyJoinRequests(): Observable<any[]> {
    return of([]).pipe(delay(300));
  }

  approveJoinRequest(requestId: string): Observable<UnitMember> {
    return of({} as UnitMember).pipe(delay(300));
  }

  rejectJoinRequest(requestId: string): Observable<void> {
    return of(void 0).pipe(delay(300));
  }
}

