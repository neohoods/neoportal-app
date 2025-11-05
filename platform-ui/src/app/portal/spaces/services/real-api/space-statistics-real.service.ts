import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { SpacesApiService } from '../../../../api-client/api/spacesApi.service';
import { UISpaceStatistics } from '../../../../models/UISpaceStatistics';
import { SpaceStatisticsService } from '../space-statistics.service';

@Injectable({
    providedIn: 'root'
})
export class SpaceStatisticsRealService implements SpaceStatisticsService {

    constructor(private spacesApi: SpacesApiService) { }

    getSpaceStatistics(spaceId: string, startDate?: string, endDate?: string): Observable<UISpaceStatistics> {
        // Use the new public endpoint for occupancy calendar
        return this.spacesApi.getSpaceOccupancyCalendar(spaceId, startDate, endDate).pipe(
            map((response: any) => {
                // Map the response to UISpaceStatistics format
                // The frontend only needs occupancyCalendar, other fields are set to defaults
                const occupancyCalendar = response.occupancyCalendar?.map((day: any) => {
                    // Extract reservationId from JsonNullable object if present
                    // JsonNullable from Java backend can be serialized as:
                    // - { "present": true, "value": "uuid-string" } when present
                    // - { "present": false, "undefined": true } when undefined
                    // - null when null
                    // - "uuid-string" when directly serialized as string
                    let reservationId: string | undefined = undefined;
                    
                    if (day.reservationId) {
                        if (typeof day.reservationId === 'string') {
                            // Direct string value
                            reservationId = day.reservationId;
                        } else if (typeof day.reservationId === 'object' && day.reservationId !== null) {
                            // JsonNullable object
                            if (day.reservationId.present === true && day.reservationId.value) {
                                reservationId = day.reservationId.value;
                            }
                            // If present is false or undefined is true, reservationId remains undefined
                        }
                    }
                    
                    return {
                        date: day.date,
                        isOccupied: day.isOccupied,
                        reservationId: reservationId,
                        userName: undefined // Never exposed in public API
                    };
                }) || [];

                return {
                    spaceId: spaceId,
                    spaceName: '', // Not provided by public endpoint
                    period: {
                        startDate: startDate || '',
                        endDate: endDate || ''
                    },
                    occupancyRate: 0, // Not provided by public endpoint
                    totalRevenue: 0, // Not provided by public endpoint
                    totalReservations: 0, // Not provided by public endpoint
                    totalDaysBooked: 0, // Not provided by public endpoint
                    averageReservationDuration: 0, // Not provided by public endpoint
                    topUsers: [], // Not provided by public endpoint
                    occupancyCalendar: occupancyCalendar,
                    monthlyOccupancy: [] // Not provided by public endpoint
                } as UISpaceStatistics;
            })
        );
    }
}








