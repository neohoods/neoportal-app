import { Observable } from 'rxjs';
// Types will be generated from OpenAPI - using temporary types for now
// import { Unit } from '../../../../api-client/model/unit';
// import { PaginatedUnits } from '../../../../api-client/model/paginatedUnits';
// import { UnitMember } from '../../../../api-client/model/unitMember';
// import { UnitType } from '../../../../api-client/model/unitType';
// import { UnitMemberResidenceRole } from '../../../../api-client/model/unitMemberResidenceRole';

// Temporary types until OpenAPI models are regenerated
type Unit = any;
type PaginatedUnits = any;
type UnitMember = any;
type UnitType = 'FLAT' | 'GARAGE' | 'PARKING' | null;
type UnitMemberResidenceRole = 'PROPRIETAIRE' | 'BAILLEUR' | 'MANAGER' | 'TENANT' | null;

export interface UnitsService {
  getUnitsDirectory(
    page?: number,
    size?: number,
    type?: UnitType,
    search?: string,
    userId?: string
  ): Observable<PaginatedUnits>;
  
  getRelatedParkingGarages(userId: string): Observable<Unit[]>;
  
  updateMemberResidenceRole(
    unitId: string,
    userId: string,
    residenceRole: UnitMemberResidenceRole | null
  ): Observable<UnitMember>;
  
  createJoinRequest(unitId: string, message?: string): Observable<any>;
  
  getJoinRequests(unitId: string): Observable<any[]>;
  
  getMyJoinRequests(): Observable<any[]>;
  
  approveJoinRequest(requestId: string): Observable<UnitMember>;
  
  rejectJoinRequest(requestId: string): Observable<void>;
}

