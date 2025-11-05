import { Observable } from 'rxjs';
import { UISpaceStatistics } from '../../../models/UISpaceStatistics';

export interface AdminSpaceStatisticsService {
    getSpaceStatistics(spaceId: string, startDate?: string, endDate?: string): Observable<UISpaceStatistics>;
}

