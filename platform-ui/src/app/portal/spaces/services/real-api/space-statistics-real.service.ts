import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { SpacesAdminApiService } from '../../../../api-client/api/spacesAdminApi.service';
import { UISpaceStatistics } from '../../../../models/UISpaceStatistics';
import { SpaceStatisticsService } from '../space-statistics.service';

@Injectable({
    providedIn: 'root'
})
export class SpaceStatisticsRealService implements SpaceStatisticsService {

    constructor(private spacesAdminApi: SpacesAdminApiService) { }

    getSpaceStatistics(spaceId: string, startDate?: string, endDate?: string): Observable<UISpaceStatistics> {
        return this.spacesAdminApi.getSpaceStatistics(spaceId, startDate, endDate).pipe(
            map((response: any) => response as UISpaceStatistics)
        );
    }
}








