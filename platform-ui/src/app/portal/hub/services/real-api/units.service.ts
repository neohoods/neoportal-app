import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { UnitsHubApiService } from '../../../../api-client/api/unitsHubApi.service';
// Types will be generated from OpenAPI - using temporary types for now
// import { Unit } from '../../../../api-client/model/unit';
// import { PaginatedUnits } from '../../../../api-client/model/paginatedUnits';
// import { UnitMember } from '../../../../api-client/model/unitMember';
// import { UnitType } from '../../../../api-client/model/unitType';
// import { UnitMemberResidenceRole } from '../../../../api-client/model/unitMemberResidenceRole';
import { UpdateMemberResidenceRoleRequest } from '../../../../api-client/model/updateMemberResidenceRoleRequest';
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
export class APIUnitsService implements UnitsService {
  constructor(private unitsHubApiService: UnitsHubApiService) {}

  getUnitsDirectory(
    page?: number,
    size?: number,
    type?: UnitType,
    search?: string,
    userId?: string
  ): Observable<PaginatedUnits> {
    // Convert our temporary type to the API type (will be fixed after OpenAPI regeneration)
    const apiType = type as any;
    return this.unitsHubApiService.getUnitsDirectory(page, size, apiType, search, userId);
  }

  getRelatedParkingGarages(userId: string): Observable<Unit[]> {
    // API returns Flux<Unit> or Observable<Unit[]>, handle both
    return new Observable((observer) => {
      this.unitsHubApiService.getRelatedParkingGarages(userId).subscribe({
        next: (response: any) => {
          // Check if response is an array (Observable<Unit[]>)
          if (Array.isArray(response)) {
            observer.next(response);
            observer.complete();
          } else if (response && typeof response.subscribe === 'function') {
            // Response is Flux<Unit>, collect it
            const units: Unit[] = [];
            response.subscribe({
              next: (unit: any) => units.push(unit),
              complete: () => {
                observer.next(units);
                observer.complete();
              },
              error: (err: any) => observer.error(err),
            });
          } else {
            observer.next([]);
            observer.complete();
          }
        },
        error: (err: any) => observer.error(err),
      });
    });
  }

  updateMemberResidenceRole(
    unitId: string,
    userId: string,
    residenceRole: UnitMemberResidenceRole | null
  ): Observable<UnitMember> {
    // This method is no longer available in the hub API - it's now an admin-only endpoint
    // Throwing an error to indicate this should not be called from hub
    throw new Error('updateMemberResidenceRole is only available through the admin API');
  }

  createJoinRequest(unitId: string, message?: string): Observable<any> {
    // Send null if no message to avoid sending empty object {}
    const request = message ? { message } : null;
    return this.unitsHubApiService.createJoinRequest(unitId, request as any);
  }

  getJoinRequests(unitId: string): Observable<any[]> {
    return new Observable((observer) => {
      this.unitsHubApiService.getJoinRequests(unitId).subscribe({
        next: (response: any) => {
          if (Array.isArray(response)) {
            observer.next(response);
            observer.complete();
          } else if (response && typeof response.subscribe === 'function') {
            const requests: any[] = [];
            response.subscribe({
              next: (request: any) => requests.push(request),
              complete: () => {
                observer.next(requests);
                observer.complete();
              },
              error: (err: any) => observer.error(err),
            });
          } else {
            observer.next([]);
            observer.complete();
          }
        },
        error: (err: any) => observer.error(err),
      });
    });
  }

  getMyJoinRequests(): Observable<any[]> {
    return new Observable((observer) => {
      this.unitsHubApiService.getMyJoinRequests().subscribe({
        next: (response: any) => {
          if (Array.isArray(response)) {
            observer.next(response);
            observer.complete();
          } else if (response && typeof response.subscribe === 'function') {
            const requests: any[] = [];
            response.subscribe({
              next: (request: any) => requests.push(request),
              complete: () => {
                observer.next(requests);
                observer.complete();
              },
              error: (err: any) => observer.error(err),
            });
          } else {
            observer.next([]);
            observer.complete();
          }
        },
        error: (err: any) => observer.error(err),
      });
    });
  }

  approveJoinRequest(requestId: string): Observable<UnitMember> {
    return this.unitsHubApiService.approveJoinRequest(requestId);
  }

  rejectJoinRequest(requestId: string): Observable<void> {
    return this.unitsHubApiService.rejectJoinRequest(requestId).pipe(
      map(() => void 0)
    );
  }
}

