import { Observable } from 'rxjs';
import { Unit } from '../../../api-client/model/unit';
import { PaginatedUnits } from '../../../api-client/model/paginatedUnits';

import { UnitType } from '../../../api-client/model/unitType';

export interface UnitsService {
  getUnits(page?: number, size?: number, search?: string): Observable<PaginatedUnits>;
  getUnit(id: string): Observable<Unit>;
  createUnit(name: string, type?: UnitType): Observable<Unit>;
  updateUnit(id: string, name: string, type?: UnitType): Observable<Unit>;
  deleteUnit(id: string): Observable<void>;
}

