import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { UnitsAdminApiService } from '../../../../api-client/api/unitsAdminApi.service';
import { Unit } from '../../../../api-client/model/unit';
import { PaginatedUnits } from '../../../../api-client/model/paginatedUnits';
import { UnitRequest } from '../../../../api-client/model/unitRequest';
import { UnitType } from '../../../../api-client/model/unitType';
import { UnitsService } from '../units.service';

@Injectable({
  providedIn: 'root',
})
export class ApiUnitsService implements UnitsService {
  constructor(private unitsAdminApiService: UnitsAdminApiService) {}

  getUnits(page?: number, size?: number, search?: string): Observable<PaginatedUnits> {
    return this.unitsAdminApiService.getAdminUnits(page, size, search);
  }

  getUnit(id: string): Observable<Unit> {
    return this.unitsAdminApiService.getAdminUnit(id);
  }

  createUnit(name: string, type?: UnitType): Observable<Unit> {
    const unitRequest: UnitRequest = { name, type };
    return this.unitsAdminApiService.createUnit(unitRequest);
  }

  updateUnit(id: string, name: string, type?: UnitType): Observable<Unit> {
    const unitRequest: UnitRequest = { name, type };
    return this.unitsAdminApiService.updateUnit(id, unitRequest);
  }

  deleteUnit(id: string): Observable<void> {
    return this.unitsAdminApiService.deleteUnit(id);
  }
}

