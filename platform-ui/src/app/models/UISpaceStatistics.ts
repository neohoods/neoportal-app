export interface UISpaceStatistics {
    spaceId: string;
    spaceName: string;
    period: {
        startDate: string;
        endDate: string;
    };
    occupancyRate: number;
    totalRevenue: number;
    totalReservations: number;
    totalDaysBooked: number;
    averageReservationDuration?: number;
    topUsers: UITopUser[];
    occupancyCalendar: UIOccupancyCalendarDay[];
    monthlyOccupancy: UIMonthlyOccupancy[];
}

export interface UITopUser {
    userId: string;
    userName: string;
    userEmail?: string;
    reservationCount: number;
    totalDays: number;
    totalSpent: number;
}

export interface UIOccupancyCalendarDay {
    date: string;
    isOccupied: boolean;
    reservationId?: string;
    userName?: string;
}

export interface UIMonthlyOccupancy {
    month: string;
    daysOccupied: number;
    daysAvailable: number;
}

