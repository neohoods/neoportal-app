import { CommonModule, ViewportScroller } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiDayRange } from '@taiga-ui/cdk';
import { TuiAlertService, TuiButton, TuiIcon, TuiLoader, TuiNotification } from '@taiga-ui/core';

import { inject } from '@angular/core';
import { TuiArcChart } from '@taiga-ui/addon-charts';
import { TuiSizeXL } from '@taiga-ui/core';
import { TuiChip } from '@taiga-ui/kit';
import { QuotaInfoPeriodEnum } from '../../../../api-client/model/quotaInfo';
import { AUTH_SERVICE_TOKEN } from '../../../../global.provider';
import { UserInfo } from '../../../../services/auth.service';
import { UnitsHubApiService } from '../../../../api-client/api/unitsHubApi.service';
import { SpaceCalendarComponent } from '../../components/space-calendar/space-calendar.component';
import { CreateReservationRequest } from '../../services/reservations.service';
import { Space } from '../../services/spaces.service';
import { RESERVATIONS_SERVICE_TOKEN, SPACES_SERVICE_TOKEN, SPACE_STATISTICS_SERVICE_TOKEN } from '../../spaces.provider';
import { ConfigService } from '../../../../services/config.service';

@Component({
    selector: 'app-space-detail',
    standalone: true,
    imports: [
        CommonModule,
        TranslateModule,
        TuiButton,
        TuiIcon,
        TuiLoader,
        TuiNotification,
        ReactiveFormsModule,
        SpaceCalendarComponent,
        TuiChip,
        TuiArcChart
    ],
    templateUrl: './space-detail.component.html',
    styleUrls: ['./space-detail.component.scss']
})
export class SpaceDetailComponent implements OnInit {
    private spacesService = inject(SPACES_SERVICE_TOKEN);
    private reservationsService = inject(RESERVATIONS_SERVICE_TOKEN);
    private statisticsService = inject(SPACE_STATISTICS_SERVICE_TOKEN);
    private authService = inject(AUTH_SERVICE_TOKEN);
    private unitsApiService = inject(UnitsHubApiService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private translate = inject(TranslateService);
    private alertService = inject(TuiAlertService);
    private viewportScroller = inject(ViewportScroller);

    space = signal<Space | null>(null);
    loading = signal(false);
    error = signal<string | null>(null);
    currentUser!: UserInfo;
    selectedDate = signal<TuiDayRange | null | undefined>(null);
    priceBreakdown = signal<any | null>(null);
    loadingPriceBreakdown = signal(false);

    // Occupancy data for calendar
    occupancyMap = signal<Map<string, boolean>>(new Map());
    myReservationsMap = signal<Map<string, string>>(new Map()); // Map<date, reservationId> for current user's reservations
    sharedOccupancyMap = signal<Map<string, boolean>>(new Map()); // New signal for shared space reservations
    loadingOccupancy = signal(false);

    protected readonly reservationForm = new FormGroup({
        dateRange: new FormControl<TuiDayRange | null>(null)
    });

    // User quota data
    userQuotaUsed = signal<number>(0);
    userQuotaMax = signal<number>(0);
    userQuotaPeriod = signal<QuotaInfoPeriodEnum | undefined>(undefined);
    loadingUserQuota = signal(false);

    // Chart size
    chartSize: TuiSizeXL = 'm';

    // User can reserve flag
    canReserve = signal<boolean | null>(null);
    loadingCanReserve = signal(false);

    // Stripe demo mode
    stripeDemoMode = ConfigService.configuration.stripeDemoMode;

    ngOnInit(): void {
        // Scroll to top when component initializes
        this.viewportScroller.scrollToPosition([0, 0]);
        this.currentUser = this.authService.getCurrentUserInfo();
        const spaceId = this.route.snapshot.paramMap.get('id');
        if (spaceId) {
            this.loadSpace(spaceId);
        }
        // Check if user can make reservations
        this.checkCanReserve();
    }

    private loadSpace(spaceId: string): void {
        this.loading.set(true);
        this.error.set(null);

        this.spacesService.getSpaceById(spaceId).subscribe({
            next: (space) => {
                this.space.set(space || null);
                this.loading.set(false);
                // Load occupancy data for calendar
                this.loadOccupancyData(spaceId);
                // Load user quota data
                this.loadUserQuota(spaceId);
            },
            error: (error) => {
                console.error('Error loading space:', error);
                this.alertService.open(
                    this.translate.instant('spaces.detail.notFound'),
                    { appearance: 'negative' }
                ).subscribe();
                this.error.set(this.translate.instant('spaces.detail.notFound'));
                this.loading.set(false);
            }
        });
    }

    private loadOccupancyData(spaceId: string): void {
        this.loadingOccupancy.set(true);

        // Load occupancy data for 1 month before + current month + 2 months after (4 months total)
        const now = new Date();
        const startDate = new Date(now.getFullYear(), now.getMonth() - 1, now.getDate());
        const endDate = new Date(now.getFullYear(), now.getMonth() + 2, now.getDate());

        const startDateString = `${startDate.getFullYear()}-${String(startDate.getMonth() + 1).padStart(2, '0')}-${String(startDate.getDate()).padStart(2, '0')}`;
        const endDateString = `${endDate.getFullYear()}-${String(endDate.getMonth() + 1).padStart(2, '0')}-${String(endDate.getDate()).padStart(2, '0')}`;

        // Load regular occupancy data
        this.statisticsService.getSpaceStatistics(spaceId, startDateString, endDateString).subscribe({
            next: (stats) => {
                if (stats.occupancyCalendar) {
                    const newMap = new Map<string, boolean>();
                    const myReservations = new Map<string, string>();
                    stats.occupancyCalendar.forEach(day => {
                        newMap.set(day.date, day.isOccupied);
                        // If reservationId is present, it means this is the current user's reservation
                        if (day.reservationId) {
                            myReservations.set(day.date, day.reservationId);
                        }
                    });
                    this.occupancyMap.set(newMap);
                    this.myReservationsMap.set(myReservations);
                }
                this.loadingOccupancy.set(false);
            },
            error: (error) => {
                console.error('Error loading occupancy data:', error);
                this.loadingOccupancy.set(false);
                // Don't show error to user for occupancy data, just log it
            }
        });

        // Load shared space reservations if the space has shared spaces
        const space = this.space();
        if (space?.rules?.shareSpaceWith?.length) {
            this.loadSharedSpaceReservations(spaceId, startDateString, endDateString);
        }
    }

    private loadSharedSpaceReservations(spaceId: string, startDate: string, endDate: string): void {
        // Use the new endpoint to get shared space reservations
        this.spacesService.getSharedSpaceReservations(spaceId, startDate, endDate).subscribe({
            next: (reservations) => {
                if (reservations && reservations.length > 0) {
                    const sharedMap = new Map<string, boolean>();

                    // Convert reservations to occupancy map
                    reservations.forEach(reservation => {
                        const start = new Date(reservation.startDate);
                        const end = new Date(reservation.endDate);

                        // Mark all days in the reservation range as occupied
                        for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
                            const dateKey = d.toISOString().split('T')[0];
                            sharedMap.set(dateKey, true);
                        }
                    });

                    this.sharedOccupancyMap.set(sharedMap);
                }
            },
            error: (error) => {
                console.error('Error loading shared space reservations:', error);
                // Don't show error to user, just log it
            }
        });
    }

    private loadAdditionalOccupancyData(spaceId: string, targetMonth: Date): void {
        // Check if we already have data for this month
        const monthKey = `${targetMonth.getFullYear()}-${String(targetMonth.getMonth() + 1).padStart(2, '0')}`;
        const currentMap = this.occupancyMap();

        // Check if we have any data for this month
        const hasDataForMonth = Array.from(currentMap.keys()).some(dateKey =>
            dateKey.startsWith(monthKey)
        );

        if (hasDataForMonth) {
            return; // Already have data for this month
        }

        // Load data for the target month
        const startDate = new Date(targetMonth.getFullYear(), targetMonth.getMonth(), 1);
        const endDate = new Date(targetMonth.getFullYear(), targetMonth.getMonth() + 1, 0); // Last day of the month

        const startDateString = `${startDate.getFullYear()}-${String(startDate.getMonth() + 1).padStart(2, '0')}-${String(startDate.getDate()).padStart(2, '0')}`;
        const endDateString = `${endDate.getFullYear()}-${String(endDate.getMonth() + 1).padStart(2, '0')}-${String(endDate.getDate()).padStart(2, '0')}`;

        this.statisticsService.getSpaceStatistics(spaceId, startDateString, endDateString).subscribe({
            next: (stats) => {
                if (stats.occupancyCalendar) {
                    const currentMap = this.occupancyMap();
                    const currentMyReservations = this.myReservationsMap();
                    const newMap = new Map(currentMap); // Copy existing data
                    const newMyReservations = new Map(currentMyReservations); // Copy existing reservations

                    // Add new data
                    stats.occupancyCalendar.forEach(day => {
                        newMap.set(day.date, day.isOccupied);
                        // If reservationId is present, it means this is the current user's reservation
                        if (day.reservationId) {
                            newMyReservations.set(day.date, day.reservationId);
                        }
                    });

                    this.occupancyMap.set(newMap);
                    this.myReservationsMap.set(newMyReservations);
                }
            },
            error: (error) => {
                console.error('Error loading additional occupancy data:', error);
            }
        });
    }

    onReserveClick(): void {
        const space = this.space();
        if (space && this.selectedDate()) {
            this.loading.set(true);

            // Format dates directly from TuiDay to avoid timezone issues
            const from = this.selectedDate()!.from;
            const to = this.selectedDate()!.to;
            const startDate = `${from.year}-${String(from.month + 1).padStart(2, '0')}-${String(from.day).padStart(2, '0')}`;
            const endDate = `${to.year}-${String(to.month + 1).padStart(2, '0')}-${String(to.day).padStart(2, '0')}`;

            const request: CreateReservationRequest = {
                spaceId: space.id,
                startDate: startDate, // YYYY-MM-DD format
                endDate: endDate // YYYY-MM-DD format
            };

            this.reservationsService.createReservation(request).subscribe({
                next: (response: any) => {
                    this.loading.set(false);
                    // Navigate to reservation detail page (will show payment view if PENDING_PAYMENT)
                    this.router.navigate(['/spaces/reservations', response.reservation.id]);
                },
                error: (error: any) => {
                    this.loading.set(false);
                    console.error('Error creating reservation:', error);
                    this.alertService.open(
                        this.translate.instant('spaces.reservation.createError'),
                        { appearance: 'negative' }
                    ).subscribe();
                }
            });
        }
    }

    onDateSelected(dateRange: TuiDayRange | null | undefined): void {
        this.selectedDate.set(dateRange || null);
        // Load price breakdown when dates change
        if (dateRange && dateRange.from && dateRange.to) {
            this.loadPriceBreakdown(dateRange.from, dateRange.to);
        } else {
            this.priceBreakdown.set(null);
        }
    }

    private loadPriceBreakdown(from: TuiDayRange['from'], to: TuiDayRange['to']): void {
        const space = this.space();
        if (!space || !from || !to) {
            this.priceBreakdown.set(null);
            return;
        }

        // Convert TuiDay to date string (YYYY-MM-DD)
        const startDate = `${from.year}-${String(from.month + 1).padStart(2, '0')}-${String(from.day).padStart(2, '0')}`;
        const endDate = `${to.year}-${String(to.month + 1).padStart(2, '0')}-${String(to.day).padStart(2, '0')}`;

        this.loadingPriceBreakdown.set(true);
        this.spacesService.getPriceBreakdown(space.id, startDate, endDate).subscribe({
            next: (breakdown) => {
                this.priceBreakdown.set(breakdown);
                this.loadingPriceBreakdown.set(false);
            },
            error: (error) => {
                console.error('Error loading price breakdown:', error);
                this.priceBreakdown.set(null);
                this.loadingPriceBreakdown.set(false);
            }
        });
    }

    isGuestRoom(): boolean {
        const space = this.space();
        return space?.type === 'GUEST_ROOM';
    }

    onBackClick(): void {
        this.router.navigate(['/spaces']);
    }

    onImageClick(clickedImageIndex: number): void {
        const currentSpace = this.space();
        if (!currentSpace?.images) return;

        // Swap the clicked image with the first image (main image)
        const images = [...currentSpace.images];
        const temp = images[0];
        images[0] = images[clickedImageIndex];
        images[clickedImageIndex] = temp;

        this.space.set({
            ...currentSpace,
            images
        });
    }

    getSpaceTypeLabel(type: string): string {
        return this.translate.instant(`spaces.types.${type}`);
    }

    getSpaceStatusLabel(status: string): string {
        return this.translate.instant(`spaces.status.${status}`);
    }

    getSpaceTypeIcon(type: string): string {
        const icons: { [key: string]: string } = {
            'GUEST_ROOM': '@tui.home',
            'COMMON_ROOM': '@tui.users',
            'COWORKING': '@tui.monitor',
            'PARKING': '@tui.car'
        };
        return icons[type] || '@tui.map-pin';
    }

    getAllowedDaysLabels(): string[] {
        const space = this.space();
        if (!space?.rules?.allowedDays) return [];

        const dayMap: { [key: string]: string } = {
            'MONDAY': this.translate.instant('spaces.days.monday'),
            'TUESDAY': this.translate.instant('spaces.days.tuesday'),
            'WEDNESDAY': this.translate.instant('spaces.days.wednesday'),
            'THURSDAY': this.translate.instant('spaces.days.thursday'),
            'FRIDAY': this.translate.instant('spaces.days.friday'),
            'SATURDAY': this.translate.instant('spaces.days.saturday'),
            'SUNDAY': this.translate.instant('spaces.days.sunday')
        };

        return space.rules.allowedDays.map(day => dayMap[day] || day);
    }

    shouldShowAllowedDays(): boolean {
        const space = this.space();
        if (!space?.rules?.allowedDays || space.rules.allowedDays.length === 0) {
            return false;
        }
        // Don't show if all 7 days are allowed (Monday to Sunday)
        const allDays = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
        return space.rules.allowedDays.length < allDays.length;
    }

    getCleaningDaysLabels(): string[] {
        const space = this.space();
        if (!space?.rules?.cleaningDays) return [];

        const dayMap: { [key: string]: string } = {
            'MONDAY': this.translate.instant('spaces.days.monday'),
            'TUESDAY': this.translate.instant('spaces.days.tuesday'),
            'WEDNESDAY': this.translate.instant('spaces.days.wednesday'),
            'THURSDAY': this.translate.instant('spaces.days.thursday'),
            'FRIDAY': this.translate.instant('spaces.days.friday'),
            'SATURDAY': this.translate.instant('spaces.days.saturday'),
            'SUNDAY': this.translate.instant('spaces.days.sunday')
        };

        return space.rules.cleaningDays.map(day => dayMap[day] || day);
    }

    getSharedSpacesLabels(): string[] {
        const space = this.space();
        if (!space?.rules?.shareSpaceWith) return [];

        // For now, we'll return the UUIDs as labels
        // In a real implementation, you would load the space details to get the names
        return space.rules.shareSpaceWith.map(spaceId => `Space ${spaceId.substring(0, 8)}...`);
    }

    private loadUserQuota(spaceId: string): void {
        const space = this.space();
        if (!space?.quota) {
            return;
        }

        this.loadingUserQuota.set(true);

        // Calculate the period start and end dates
        const now = new Date();
        let startDate: Date;
        let endDate: Date;

        switch (space.quota.period) {
            case 'YEAR':
                startDate = new Date(now.getFullYear(), 0, 1);
                endDate = new Date(now.getFullYear(), 11, 31);
                break;
            case 'MONTH':
                startDate = new Date(now.getFullYear(), now.getMonth(), 1);
                endDate = new Date(now.getFullYear(), now.getMonth() + 1, 0);
                break;
            case 'WEEK':
                const dayOfWeek = now.getDay();
                const daysToMonday = dayOfWeek === 0 ? 6 : dayOfWeek - 1;
                startDate = new Date(now);
                startDate.setDate(now.getDate() - daysToMonday);
                startDate.setHours(0, 0, 0, 0);
                endDate = new Date(startDate);
                endDate.setDate(startDate.getDate() + 6);
                endDate.setHours(23, 59, 59, 999);
                break;
            default:
                startDate = new Date(now.getFullYear(), 0, 1);
                endDate = new Date(now.getFullYear(), 11, 31);
        }

        const startDateString = startDate.toISOString().split('T')[0];
        const endDateString = endDate.toISOString().split('T')[0];

        // Load unit's reservations for this space in the current period
        // First, get user's primary unit
        this.unitsApiService.getUnits().subscribe({
            next: (units: any[]) => {
                if (!units || units.length === 0) {
                    // No unit, fallback to user reservations
                    this.loadUserReservationsForQuota(spaceId, startDateString, endDateString);
                    return;
                }

                // Get primary unit (first unit where user is TENANT or OWNER)
                // For now, use first unit - in production, filter by user type
                const primaryUnit = units[0];
                if (!primaryUnit || !primaryUnit.id) {
                    this.loadUserReservationsForQuota(spaceId, startDateString, endDateString);
                    return;
                }

                // Load unit reservations
                (this.unitsApiService as any).getUnitReservations(primaryUnit.id, 0, 1000).subscribe({
                    next: (response: any) => {
                        const reservations = response.content || [];
                        
                        // Filter reservations for this space and current period
                        const unitReservations = reservations.filter((reservation: any) =>
                            reservation.spaceId === spaceId &&
                            reservation.startDate >= startDateString &&
                            reservation.startDate <= endDateString &&
                            (reservation.status === 'CONFIRMED' || reservation.status === 'ACTIVE' || reservation.status === 'COMPLETED')
                        );

                        // Calculate used quota (count of reservations)
                        const usedQuota = unitReservations.length;

                        this.userQuotaUsed.set(usedQuota);
                        this.userQuotaMax.set(space.quota!.max);
                        this.userQuotaPeriod.set(space.quota!.period);
                        this.loadingUserQuota.set(false);
                    },
                    error: (error: any) => {
                        console.error('Error loading unit quota:', error);
                        // Fallback to user reservations
                        this.loadUserReservationsForQuota(spaceId, startDateString, endDateString);
                    }
                });
            },
            error: (error: any) => {
                console.error('Error loading units:', error);
                // Fallback to user reservations
                this.loadUserReservationsForQuota(spaceId, startDateString, endDateString);
            }
        });
    }

    private loadUserReservationsForQuota(spaceId: string, startDateString: string, endDateString: string): void {
        // Fallback: load user's reservations if no unit
        this.reservationsService.getMyReservations().subscribe({
            next: (reservations: any[]) => {
                if (!reservations) {
                    this.loadingUserQuota.set(false);
                    return;
                }

                // Filter reservations for this space and current period
                const userReservations = reservations.filter((reservation: any) =>
                    reservation.spaceId === spaceId &&
                    reservation.userId === this.currentUser.user.id &&
                    reservation.startDate >= startDateString &&
                    reservation.startDate <= endDateString
                );

                // Calculate used quota (count of reservations)
                const usedQuota = userReservations.length;

                this.userQuotaUsed.set(usedQuota);
                const space = this.space();
                if (space?.quota) {
                    this.userQuotaMax.set(space.quota.max);
                    this.userQuotaPeriod.set(space.quota.period);
                }
                this.loadingUserQuota.set(false);
            },
            error: (error: any) => {
                console.error('Error loading user quota:', error);
                this.loadingUserQuota.set(false);
            }
        });
    }

    getUserQuotaProgress(): number {
        const max = this.userQuotaMax();
        if (max === 0) return 0;
        return Math.min((this.userQuotaUsed() / max) * 100, 100);
    }

    getUserQuotaText(): string {
        const used = this.userQuotaUsed();
        const max = this.userQuotaMax();
        const period = this.userQuotaPeriod();

        if (max === 0 || !period) return '';

        // Convert enum to lowercase string for translation key
        const periodKey = String(period).toLowerCase();
        const periodText = this.translate.instant(`spaces.quota.period.${periodKey}`);
        return `${used} / ${max} ${this.translate.instant('spaces.quota.per')} ${periodText}`;
    }

    getQuotaChartValue(): number[] {
        const used = this.userQuotaUsed();
        const max = this.userQuotaMax();

        if (max === 0) return [];

        return [used];
    }

    private checkCanReserve(): void {
        this.loadingCanReserve.set(true);
        // Check if user has a primary unit set
        const primaryUnitId = this.currentUser.user.primaryUnitId;
        const canReserveValue = primaryUnitId != null && primaryUnitId !== '';
        this.canReserve.set(canReserveValue);
        this.loadingCanReserve.set(false);
    }
}
