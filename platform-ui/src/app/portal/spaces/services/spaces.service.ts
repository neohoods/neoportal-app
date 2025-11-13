import { Observable } from 'rxjs';
import { AvailabilityResponse, PriceBreakdown } from '../../../api-client/model/models';
import { UISpace } from '../../../models/UISpace';

// Re-export types for backward compatibility
export type { AvailabilityResponse } from '../../../api-client/model/models';
export type { UISpace } from '../../../models/UISpace';
// Legacy alias for backward compatibility
export type Space = UISpace;

export interface PaginatedSpacesResponse {
    content: UISpace[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
    first: boolean;
    last: boolean;
    numberOfElements: number;
}

export interface SpacesService {
    getSpaces(type?: string, available?: boolean, startDate?: string, endDate?: string, search?: string, page?: number, size?: number): Observable<PaginatedSpacesResponse>;
    getSpaceById(id: string): Observable<UISpace | undefined>;
    getSpaceAvailability(spaceId: string, startDate: string, endDate: string): Observable<AvailabilityResponse>;
    getSharedSpaceReservations(spaceId: string, startDate: string, endDate: string): Observable<any[]>; // New method for shared space reservations
    getPriceBreakdown(spaceId: string, startDate: string, endDate: string): Observable<PriceBreakdown>;
    
    // Cache methods
    loadSpacesByIds(spaceIds: string[]): Observable<Map<string, UISpace>>;
    getCachedSpace(id: string): UISpace | undefined;
    clearSpacesCache(): void;
    preloadAllSpaces(): Observable<void>;
}
