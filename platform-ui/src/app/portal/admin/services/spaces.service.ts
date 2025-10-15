import { Observable } from 'rxjs';
import { UIDigitalLock } from '../../../models/UIDigitalLock';
import { UISpace } from '../../../models/UISpace';

export interface AdminSpacesService {
    getSpaces(page?: number, size?: number, type?: string, status?: string, search?: string): Observable<{ content: UISpace[], totalElements: number, totalPages: number, size: number, number: number, first: boolean, last: boolean, numberOfElements: number }>;
    getSpace(id: string): Observable<UISpace>;
    createSpace(space: Partial<UISpace>): Observable<UISpace>;
    updateSpace(id: string, space: Partial<UISpace>): Observable<UISpace>;
    deleteSpace(id: string): Observable<void>;
    toggleSpaceStatus(id: string): Observable<UISpace>;
    getSpaceDigitalLock(spaceId: string): Observable<UIDigitalLock | null>;
    associateSpaceWithDigitalLock(spaceId: string, digitalLockId: string | null): Observable<void>;
}
