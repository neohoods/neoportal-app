import { Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import statisticsData from '../../../../mock/space-statistics.json';
import { UISpaceStatistics } from '../../../../models/UISpaceStatistics';
import { AdminSpaceStatisticsService } from '../space-statistics.service';

@Injectable({
    providedIn: 'root'
})
export class MockAdminSpaceStatisticsService implements AdminSpaceStatisticsService {
    private statistics: UISpaceStatistics[] = statisticsData as UISpaceStatistics[];

    getSpaceStatistics(spaceId: string, startDate?: string, endDate?: string): Observable<UISpaceStatistics> {
        const stats = this.statistics.find(s => s.spaceId === spaceId);

        if (!stats) {
            return throwError(() => new Error('Space statistics not found')).pipe(delay(300));
        }

        // If date range is provided, filter the data accordingly
        if (startDate || endDate) {
            const filteredStats = { ...stats };

            if (startDate) {
                filteredStats.period.startDate = startDate;
            }
            if (endDate) {
                filteredStats.period.endDate = endDate;
            }

            // Filter occupancy calendar based on date range
            if (startDate || endDate) {
                filteredStats.occupancyCalendar = stats.occupancyCalendar.filter((day: any) => {
                    const dayDate = new Date(day.date);
                    const start = startDate ? new Date(startDate) : new Date('1900-01-01');
                    const end = endDate ? new Date(endDate) : new Date('2100-12-31');
                    return dayDate >= start && dayDate <= end;
                });

                // Recalculate statistics based on filtered data
                filteredStats.totalDaysBooked = filteredStats.occupancyCalendar.filter((d: any) => d.isOccupied).length;

                // Calculate days in period
                const periodStart = new Date(filteredStats.period.startDate);
                const periodEnd = new Date(filteredStats.period.endDate);
                const daysInPeriod = Math.ceil((periodEnd.getTime() - periodStart.getTime()) / (1000 * 60 * 60 * 24)) + 1;

                filteredStats.occupancyRate = (filteredStats.totalDaysBooked / daysInPeriod) * 100;
            }

            return of(filteredStats).pipe(delay(500));
        }

        return of(stats).pipe(delay(500));
    }
}

