import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { SpacesAdminApiService } from '../../../../api-client/api/spacesAdminApi.service';
import { SpaceStatistics } from '../../../../api-client/model/spaceStatistics';
import { UISpaceStatistics } from '../../../../models/UISpaceStatistics';
import { AdminSpaceStatisticsService } from '../space-statistics.service';

@Injectable({
    providedIn: 'root'
})
export class RealApiAdminSpaceStatisticsService implements AdminSpaceStatisticsService {

    constructor(private spacesAdminApi: SpacesAdminApiService) { }

    getSpaceStatistics(spaceId: string, startDate?: string, endDate?: string): Observable<UISpaceStatistics> {
        // Use the admin endpoint which returns full statistics
        return this.spacesAdminApi.getSpaceStatistics(spaceId, startDate, endDate).pipe(
            map((response: SpaceStatistics) => {
                // Map the admin API response to UISpaceStatistics format
                return {
                    spaceId: response.spaceId || spaceId,
                    spaceName: response.spaceName || '',
                    period: {
                        startDate: response.period?.startDate || startDate || '',
                        endDate: response.period?.endDate || endDate || ''
                    },
                    occupancyRate: response.occupancyRate || 0,
                    totalRevenue: response.totalRevenue || 0,
                    totalReservations: response.totalReservations || 0,
                    totalDaysBooked: response.totalDaysBooked || 0,
                    averageReservationDuration: response.averageReservationDuration || 0,
                    topUsers: (response.topUsers || []).map(user => ({
                        userId: user.userId || '',
                        userName: user.userName || '',
                        userEmail: user.userEmail || undefined,
                        reservationCount: user.reservationCount || 0,
                        totalDays: user.totalDays || 0,
                        totalSpent: user.totalSpent || 0
                    })),
                    occupancyCalendar: (response.occupancyCalendar || []).map(day => ({
                        date: day.date || '',
                        isOccupied: day.isOccupied || false,
                        reservationId: day.reservationId || undefined,
                        userName: day.userName || undefined
                    })),
                    monthlyOccupancy: (response.monthlyOccupancy || []).map(month => ({
                        month: month.month || '',
                        daysOccupied: month.daysOccupied || 0,
                        daysAvailable: month.daysAvailable || 0
                    }))
                } as UISpaceStatistics;
            })
        );
    }
}

