import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal, ViewEncapsulation } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAxes, TuiBarChart } from '@taiga-ui/addon-charts';
import { tuiCeil, TuiContext, TuiDay, TuiDayRange, TuiHandler, TuiMonth } from '@taiga-ui/cdk';
import { TUI_DAY_TYPE_HANDLER, TuiAlertService, TuiAutoColorPipe, TuiButton, TuiCalendar, tuiFormatNumber, TuiHint, TuiIcon, TuiInitialsPipe, TuiLoader, TuiTextfield } from '@taiga-ui/core';
import { TuiAvatar, TuiCalendarRange, TuiChip, tuiCreateDefaultDayRangePeriods, TuiInputDateRange } from '@taiga-ui/kit';
import { ReservationsAdminApiService } from '../../../../api-client/api/reservationsAdminApi.service';
import { UIDigitalLock } from '../../../../models/UIDigitalLock';
import { UISpaceStatistics } from '../../../../models/UISpaceStatistics';
import { UISpace } from '../../../spaces/services/spaces.service';
import { SPACE_STATISTICS_SERVICE_TOKEN } from '../../../spaces/spaces.provider';
import { ADMIN_SPACES_SERVICE_TOKEN } from '../../admin.providers';
import { AdminDigitalLockService, DIGITAL_LOCK_SERVICE_TOKEN } from '../../services/digital-lock.service';

interface GeneratedAccessCode {
    code: string;
    expiresAt: string;
}

@Component({
    selector: 'app-spaces-view',
    standalone: true,
    imports: [
        CommonModule,
        ReactiveFormsModule,
        TuiButton,
        TuiIcon,
        TuiLoader,
        TuiTextfield,
        TuiInputDateRange,
        TuiCalendarRange,
        TuiCalendar,
        TuiAvatar,
        TuiInitialsPipe,
        TuiAutoColorPipe,
        TuiAxes,
        TuiBarChart,
        TuiChip,
        TuiHint,
        TranslateModule
    ],
    providers: [
        {
            provide: TUI_DAY_TYPE_HANDLER,
            useFactory: (component: SpacesViewComponent) => component.handler,
            deps: [SpacesViewComponent]
        }
    ],
    templateUrl: './spaces-view.component.html',
    styleUrls: ['./spaces-view.component.scss'],
    encapsulation: ViewEncapsulation.None
})
export class SpacesViewComponent implements OnInit {
    private spacesService = inject(ADMIN_SPACES_SERVICE_TOKEN);
    private statisticsService = inject(SPACE_STATISTICS_SERVICE_TOKEN);
    private digitalLockService: AdminDigitalLockService = inject(DIGITAL_LOCK_SERVICE_TOKEN);
    private reservationsService = inject(ReservationsAdminApiService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private alertService = inject(TuiAlertService);
    private translate = inject(TranslateService);

    space = signal<UISpace | null>(null);
    loading = signal(true);
    generatingCode = signal(false);
    generatedCode = signal<GeneratedAccessCode | null>(null);
    digitalLocks = signal<UIDigitalLock[]>([]);
    spaceId: string | null = null;

    // Statistics
    statistics = signal<UISpaceStatistics | null>(null);
    loadingStatistics = signal(true);
    dateRangeControl = new FormControl<TuiDayRange | null>(null);
    readonly items = tuiCreateDefaultDayRangePeriods();

    // Calendar for occupancy - centered on current date
    firstMonth = signal<TuiDay>(TuiDay.currentLocal());
    middleMonth = signal<TuiDay>(TuiDay.currentLocal().append({ month: 1 }));
    lastMonth = signal<TuiDay>(TuiDay.currentLocal().append({ month: 2 }));
    occupancyMap = signal<Map<string, boolean>>(new Map());
    hoveredItem: TuiDay | null = null;
    value: TuiDay | null = null;

    // Reservation details for clicked day
    selectedDayReservations = signal<any[]>([]);
    selectedDay = signal<TuiDay | null>(null);
    loadingReservations = signal(false);

    allSpaces: UISpace[] = [];

    // Helper pour afficher les espaces conflictuels (au lieu d'afficher l'ID)
    getShareSpaceWithSpaces(): UISpace[] {
        const space = this.space();
        if (!space || !Array.isArray(space.rules?.shareSpaceWith)) return [];
        return this.allSpaces.filter((s: UISpace) =>
            (space.rules?.shareSpaceWith ?? []).includes(s.id)
        );
    }

    handler: TuiHandler<TuiDay, string> = (day: TuiDay) => {
        const dayString = `${day.year}-${String(day.month + 1).padStart(2, '0')}-${String(day.day).padStart(2, '0')}`;
        const map = this.occupancyMap();
        if (map.has(dayString) && map.get(dayString)) {
            console.log('[Calendar] Occupied day (handler):', dayString);
            return 'occupied';
        }

        return day.isWeekend ? 'weekend' : 'weekday';
    };

    ngOnInit(): void {
        this.spaceId = this.route.snapshot.paramMap.get('id');
        if (this.spaceId) {
            this.loadSpace(this.spaceId);
            this.initializeDateRange();
            this.loadStatistics(this.spaceId);
        }
        // Charge tous les espaces pour pouvoir afficher intelligemment les "sharedSpaceWith"
        this.spacesService.getSpaces(0, 1000).subscribe({
            next: (response) => { this.allSpaces = response.content; },
            error: () => { this.allSpaces = []; }
        });

        // Subscribe to date range changes
        this.dateRangeControl.valueChanges.subscribe(range => {
            if (range && this.spaceId) {
                this.loadStatistics(this.spaceId);
                // Keep calendars centered on current date - don't update them based on date range
            }
        });
    }

    private initializeDateRange(): void {
        // Set default to current year
        const now = new Date();
        const startOfYear = new TuiDay(now.getFullYear(), 0, 1);
        const endOfYear = new TuiDay(now.getFullYear(), 11, 31);
        this.dateRangeControl.setValue(new TuiDayRange(startOfYear, endOfYear));

        // Initialize calendar months centered on current date
        this.updateCalendarMonths(TuiDay.currentLocal());
    }

    private updateCalendarMonths(startDay: TuiDay): void {
        // Always center calendars on current date, not on the selected date range
        const currentDay = TuiDay.currentLocal();
        this.firstMonth.set(currentDay);
        this.middleMonth.set(currentDay.append({ month: 1 }));
        this.lastMonth.set(currentDay.append({ month: 2 }));
    }

    loadSpace(id: string): void {
        this.loading.set(true);
        this.spacesService.getSpace(id).subscribe({
            next: (space: UISpace) => {
                this.space.set(space);
                this.loading.set(false);
                // Load digital locks if associated
                if (space.digitalLockId) {
                    this.loadDigitalLocks();
                }
            },
            error: (error: any) => {
                console.error('Error loading space:', error);
                this.alertService.open(this.translate.instant('spaces.admin.errorLoadingSpace'), {
                    appearance: 'error'
                }).subscribe();
                this.loading.set(false);
                this.router.navigate(['/admin/spaces']);
            }
        });
    }

    loadDigitalLocks(): void {
        this.digitalLockService.getDigitalLocks(0, 100).subscribe({
            next: (response: UIDigitalLock[]) => {
                this.digitalLocks.set(response);
            },
            error: (error: any) => {
                console.error('Error loading digital locks:', error);
                this.alertService.open(
                    this.translate.instant('spaces.admin.errorLoadingDigitalLocks'),
                    { appearance: 'negative' }
                ).subscribe();
            }
        });
    }

    generateAccessCode(): void {
        this.generatingCode.set(true);
        this.generatedCode.set(null);

        // For now, generate a mock code (in real implementation, this would call the backend)
        const mockCode: GeneratedAccessCode = {
            code: this.generateMockCode(),
            expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString() // 24 hours from now
        };

        // Simulate API call delay
        setTimeout(() => {
            this.generatedCode.set(mockCode);
            this.generatingCode.set(false);
            this.alertService.open(this.translate.instant('spaces.admin.accessCodeGenerated'), {
                appearance: 'success'
            }).subscribe();
        }, 1000);
    }

    private generateMockCode(): string {
        const characters = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';
        let code = '';
        for (let i = 0; i < 6; i++) {
            code += characters.charAt(Math.floor(Math.random() * characters.length));
        }
        return code;
    }

    goBack(): void {
        this.router.navigate(['/admin/spaces']);
    }

    editSpace(): void {
        if (this.spaceId) {
            this.router.navigate(['/admin/spaces', this.spaceId, 'edit']);
        }
    }

    getPrimaryImage(): string {
        const space = this.space();
        if (!space || !space.images || space.images.length === 0) {
            return '/assets/images/default-space.jpg';
        }
        const primaryImage = space.images.find(img => img.isPrimary);
        return primaryImage?.url || space.images[0]?.url || '/assets/images/default-space.jpg';
    }

    getGalleryImages(): any[] {
        const space = this.space();
        if (!space || !space.images) {
            return [];
        }
        return space.images.filter(img => !img.isPrimary);
    }

    getTypeLabel(): string {
        const space = this.space();
        if (!space) return '';
        return this.translate.instant(`spaces.types.${space.type}`);
    }

    getStatusLabel(): string {
        const space = this.space();
        if (!space) return '';
        return this.translate.instant(`spaces.status.${space.status}`);
    }

    getAllowedDaysLabels(): string[] {
        const space = this.space();
        if (!space || !space.rules?.allowedDays) return [];
        return space.rules.allowedDays.map(day => this.translate.instant(`spaces.days.${day}`));
    }

    getCleaningDaysLabels(): string[] {
        const space = this.space();
        if (!space || !space.rules?.cleaningDays) return [];
        return space.rules.cleaningDays.map(day => this.translate.instant(`spaces.days.${day}`));
    }

    getConflictTypesLabels(): string[] {
        const space = this.space();
        if (!space || !space.rules?.shareSpaceWith) return [];
        return space.rules.shareSpaceWith.map(spaceId => `Space ${spaceId.substring(0, 8)}...`);
    }

    digitalLockName(): string {
        const space = this.space();
        if (!space || !space.digitalLockId) {
            return '';
        }

        const digitalLocks = this.digitalLocks();
        if (digitalLocks.length === 0) {
            return `Digital Lock #${space.digitalLockId}`;
        }

        const digitalLock = digitalLocks.find(d => d.id.toString() === space.digitalLockId?.toString());
        if (!digitalLock) {
            return `Digital Lock #${space.digitalLockId}`;
        }

        const deviceId = digitalLock.ttlockConfig?.deviceId || digitalLock.nukiConfig?.deviceId || 'Unknown';
        return `${digitalLock.name} (${deviceId})`;
    }

    formatExpiryDate(dateString?: string): string {
        if (!dateString) return '';

        const date = new Date(dateString);
        return date.toLocaleString('fr-FR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    loadStatistics(spaceId: string): void {
        this.loadingStatistics.set(true);

        const range = this.dateRangeControl.value;
        let startDate: string | undefined;
        let endDate: string | undefined;

        if (range) {
            const start = range.from;
            const end = range.to;
            startDate = `${start.year}-${String(start.month + 1).padStart(2, '0')}-${String(start.day).padStart(2, '0')}`;
            endDate = `${end.year}-${String(end.month + 1).padStart(2, '0')}-${String(end.day).padStart(2, '0')}`;
        }

        this.statisticsService.getSpaceStatistics(spaceId, startDate, endDate).subscribe({
            next: (stats) => {
                this.statistics.set(stats);
                this.loadingStatistics.set(false);

                // Update the calendar occupancy map
                if (stats.occupancyCalendar) {
                    const newMap = new Map<string, boolean>();
                    stats.occupancyCalendar.forEach(day => {
                        newMap.set(day.date, day.isOccupied);
                    });
                    console.log('[Calendar] Loaded occupancy data:', newMap.size, 'days');
                    console.log('[Calendar] Occupied days:', Array.from(newMap.entries()).filter(([_, isOccupied]) => isOccupied).map(([date]) => date));
                    this.occupancyMap.set(newMap);
                }
            },
            error: (error) => {
                console.error('Error loading statistics:', error);
                this.loadingStatistics.set(false);
                this.alertService.open(this.translate.instant('spaces.admin.errorLoadingStatistics'), {
                    appearance: 'error'
                }).subscribe();
            }
        });
    }

    get dateRangeContent(): string {
        const { value } = this.dateRangeControl;
        return value
            ? String(this.items.find((period) => period.range.daySame(value)) || '')
            : '';
    }

    getOccupiedDays(): number {
        const stats = this.statistics();
        if (!stats) return 0;
        return stats.occupancyCalendar.filter(day => day.isOccupied).length;
    }

    getFreeDays(): number {
        const stats = this.statistics();
        if (!stats) return 0;
        return stats.occupancyCalendar.filter(day => !day.isOccupied).length;
    }

    // Bar chart methods
    getChartValue(): number[][] {
        const stats = this.statistics();
        if (!stats?.monthlyOccupancy) return [[]];

        const daysOccupied = stats.monthlyOccupancy.map(m => m.daysOccupied);
        const daysAvailable = stats.monthlyOccupancy.map(m => m.daysAvailable - m.daysOccupied);

        return [daysOccupied, daysAvailable];
    }

    getChartLabelsX(): string[] {
        const stats = this.statistics();
        if (!stats?.monthlyOccupancy) return [];
        return stats.monthlyOccupancy.map(m => m.month);
    }

    getChartLabelsY(): string[] {
        const stats = this.statistics();
        if (!stats?.monthlyOccupancy) return ['0', '31'];

        const maxDays = Math.max(...stats.monthlyOccupancy.map(m => m.daysAvailable));
        return ['0', maxDays.toString()];
    }

    getChartMax(): number {
        const stats = this.statistics();
        if (!stats?.monthlyOccupancy) return 31;
        return Math.max(...stats.monthlyOccupancy.map(m => m.daysAvailable));
    }

    getChartHeight(max: number): number {
        return (max / tuiCeil(max, -3)) * 100;
    }

    // Hint for chart tooltips
    readonly chartHint = ({ $implicit }: TuiContext<number>): string => {
        const value = this.getChartValue();
        if (!value || value.length === 0) return '';

        const daysOccupied = value[0]?.[$implicit] ?? 0;
        const daysAvailable = value[1]?.[$implicit] ?? 0;

        const occupiedLabel = this.translate.instant('spaces.admin.daysOccupied');
        const availableLabel = this.translate.instant('spaces.admin.daysAvailable');

        return `${occupiedLabel}: ${tuiFormatNumber(daysOccupied)}\n${availableLabel}: ${tuiFormatNumber(daysAvailable)}`;
    };

    // Calendar methods
    onMonthChange(month: TuiMonth, calendarIndex: number): void {
        const newMonth = new TuiDay(month.year, month.month, 1);

        // Synchronize all 3 calendars based on which one changed
        if (calendarIndex === 0) {
            // First calendar changed
            this.firstMonth.set(newMonth);
            this.middleMonth.set(newMonth.append({ month: 1 }));
            this.lastMonth.set(newMonth.append({ month: 2 }));
        } else if (calendarIndex === 1) {
            // Middle calendar changed
            this.firstMonth.set(newMonth.append({ month: -1 }));
            this.middleMonth.set(newMonth);
            this.lastMonth.set(newMonth.append({ month: 1 }));
        } else {
            // Last calendar changed
            this.firstMonth.set(newMonth.append({ month: -2 }));
            this.middleMonth.set(newMonth.append({ month: -1 }));
            this.lastMonth.set(newMonth);
        }
    }

    onDayClick(day: TuiDay): void {
        this.value = day;
        this.selectedDay.set(day);

        // Vérifier si le jour est occupé
        const dayString = `${day.year}-${String(day.month + 1).padStart(2, '0')}-${String(day.day).padStart(2, '0')}`;
        const map = this.occupancyMap();
        const isOccupied = map.has(dayString) && map.get(dayString);

        if (isOccupied && this.spaceId) {
            this.loadReservationsForDay(day);
        } else {
            // Réinitialiser les réservations si le jour n'est pas occupé
            this.selectedDayReservations.set([]);
        }
    }

    private loadReservationsForDay(day: TuiDay): void {
        if (!this.spaceId) return;

        this.loadingReservations.set(true);
        this.selectedDayReservations.set([]);

        // Convertir le jour en format ISO pour l'API
        const dateString = `${day.year}-${String(day.month + 1).padStart(2, '0')}-${String(day.day).padStart(2, '0')}`;

        // Charger les réservations pour cette date spécifique
        this.reservationsService.getAdminReservations(
            this.spaceId, // spaceId
            undefined, // userId
            undefined, // status
            dateString, // startDate
            dateString, // endDate
            undefined, // page
            undefined  // size
        ).subscribe({
            next: (response: any) => {
                // Filtrer les réservations qui incluent cette date spécifique
                const reservationsForDay = response.content?.filter((reservation: any) => {
                    const startDate = new Date(reservation.startDate);
                    const endDate = new Date(reservation.endDate);
                    const clickedDate = new Date(dateString);

                    return clickedDate >= startDate && clickedDate <= endDate;
                }) || [];

                this.selectedDayReservations.set(reservationsForDay);
                this.loadingReservations.set(false);
            },
            error: (error: any) => {
                console.error('Error loading reservations for day:', error);
                this.loadingReservations.set(false);
                this.alertService.open(
                    this.translate.instant('reservations.admin.loadError'),
                    { appearance: 'error' }
                ).subscribe();
            }
        });
    }

    goToReservation(reservationId: string): void {
        this.router.navigate(['/admin/reservations', reservationId]);
    }

    formatReservationDate(dateString: string): string {
        const date = new Date(dateString);
        return date.toLocaleDateString('fr-FR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        });
    }

    formatSelectedDay(day: TuiDay): string {
        return day.toLocalNativeDate().toLocaleDateString('fr-FR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        });
    }

    formatReservationCreatedDate(dateString: string): string {
        const date = new Date(dateString);
        return date.toLocaleDateString('fr-FR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    getReservationStatusLabel(status: string): string {
        return this.translate.instant(`reservations.status.${status}`);
    }

    getReservationStatusAppearance(status: string): string {
        switch (status) {
            case 'CONFIRMED':
                return 'positive';
            case 'ACTIVE':
                return 'primary';
            case 'PENDING_PAYMENT':
                return 'accent';
            case 'CANCELLED':
                return 'negative';
            case 'COMPLETED':
                return 'neutral';
            case 'PAYMENT_FAILED':
                return 'negative';
            case 'EXPIRED':
                return 'negative';
            default:
                return 'outline';
        }
    }
}

