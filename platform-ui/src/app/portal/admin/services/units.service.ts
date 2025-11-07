import { Observable } from 'rxjs';
import { Unit } from '../../../api-client/model/unit';
import { PaginatedUnits } from '../../../api-client/model/paginatedUnits';

export interface UnitsService {
  getUnits(page?: number, size?: number, search?: string): Observable<PaginatedUnits>;
  getUnit(id: string): Observable<Unit>;
  createUnit(name: string): Observable<Unit>;
  updateUnit(id: string, name: string): Observable<Unit>;
  deleteUnit(id: string): Observable<void>;
}

