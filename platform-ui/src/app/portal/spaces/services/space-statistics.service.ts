import { Observable } from 'rxjs';
import { UISpaceStatistics } from '../../../models/UISpaceStatistics';

export interface SpaceStatisticsService {
    getSpaceStatistics(spaceId: string, startDate?: string, endDate?: string): Observable<UISpaceStatistics>;
}




































