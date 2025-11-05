import { AsyncPipe, CommonModule } from '@angular/common';
import { AfterViewInit, Component, effect, ElementRef, inject, input, output, ViewChild } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiMobileCalendar } from '@taiga-ui/addon-mobile';
import { TuiBooleanHandler, tuiControlValue, TuiDay, TuiDayLike, TuiDayRange, TuiMonth } from '@taiga-ui/cdk';
import { TUI_MONTHS, tuiCalendarSheetOptionsProvider, TuiHint, TuiLoader } from '@taiga-ui/core';
import { TuiCalendarRange } from '@taiga-ui/kit';
import { TuiInputDateRangeModule } from '@taiga-ui/legacy';
import { combineLatest, map } from 'rxjs';
import { UserInfo } from '../../../../services/auth.service';
import { Space } from '../../services/spaces.service';

const BEFORE_TODAY: string = 'rgba(0, 0, 1, 0)';
const BOOKED: string = 'rgba(0, 0, 2, 0)';
const BOOKED_BY_ME: string = 'rgba(0, 0, 3, 0)';
const SHARED_OCCUPIED: string = 'rgba(0, 0, 4, 0)'; // New color for shared space reservations
const AVAILABLE: string = '';

@Component({
    selector: 'space-calendar',
    imports: [
        CommonModule,
        TuiHint,
        TuiCalendarRange,
        TuiLoader,
        FormsModule,
        ReactiveFormsModule,
        TuiInputDateRangeModule,
        AsyncPipe,
        TranslateModule,
        TuiMobileCalendar,
    ],
    templateUrl: './space-calendar.component.html',
    styleUrl: './space-calendar.component.scss',
    providers: [tuiCalendarSheetOptionsProvider({ rangeMode: true })],

})
export class SpaceCalendarComponent implements AfterViewInit {

    private readonly today = new Date();
    protected readonly defaultViewedMonth = new TuiMonth(
        this.today.getFullYear(),
        this.today.getMonth(),
    );
    protected readonly min: TuiDay = new TuiDay(
        this.today.getFullYear(),
        this.today.getMonth(),
        this.today.getDate()
    );
    protected readonly max: TuiDay = (() => {
        const nextYearMonth = this.defaultViewedMonth.append({ year: 1 });
        return new TuiDay(
            nextYearMonth.year,
            nextYearMonth.month,
            this.today.getDate()
        );
    })();


    protected readonly date$;

    private readonly months$ = inject(TUI_MONTHS);
    public readonly control;
    protected readonly mobileCalendarControl = new FormControl<TuiDayRange | null>(null);
    public space = input.required<Space>();
    public currentUser = input.required<UserInfo>();
    public occupancyMap = input<Map<string, boolean>>(new Map());
    public myReservationsMap = input<Map<string, string>>(new Map()); // Map<date, reservationId> for current user's reservations
    public sharedOccupancyMap = input<Map<string, boolean>>(new Map()); // New input for shared space reservations
    public loadingOccupancy = input<boolean>(false);
    public selectedDate = output<TuiDayRange | null | undefined>();
    public monthChanged = output<Date>();
    public dayClicked = output<TuiDay>();
    /**
     * Minimum length of the date range based on space rules
     */
    get minLength(): TuiDayLike {
        const minDays = this.space()?.rules?.minDurationDays || 1; // Default to 1 day if not specified
        return { day: minDays };
    }

    /**
     * Maximum length of the date range based on space rules
     */
    get maxLength(): TuiDayLike {
        const maxDays = this.space()?.rules?.maxDurationDays || 30; // Default to 30 days if not specified
        return { day: maxDays };
    }

    @ViewChild('calendarRef', { static: false }) calendarRef?: ElementRef;
    private lastDetectedMonth: string = '';

    constructor(
        private translate: TranslateService,
    ) {
        this.control = new FormControl<TuiDayRange | null | undefined>(null);

        this.date$ = combineLatest([
            tuiControlValue<TuiDayRange>(this.control),
            this.months$,
        ]).pipe(
            map(([value, months]) => {
                if (!value) {
                    return "";
                }

                return value.isSingleDay
                    ? `${months[value.from.month]} ${value.from.day}, ${value.from.year}`
                    : `${months[value.from.month]} ${value.from.day}, ${value.from.year} - ${months[value.to.month]
                    } ${value.to.day}, ${value.to.year}`;
            }),
        );

        // Sync mobile calendar control with main control
        this.mobileCalendarControl.valueChanges.subscribe(value => {
            if (value && value !== this.control.value) {
                this.control.setValue(value);
                this.selectedDate.emit(value);
            } else if (!value && this.control.value) {
                this.control.setValue(null);
                this.selectedDate.emit(null);
            }
        });

        // Sync main control with mobile calendar control
        this.control.valueChanges.subscribe(value => {
            if (value && value !== this.mobileCalendarControl.value) {
                this.mobileCalendarControl.setValue(value, { emitEvent: false });
            } else if (!value && this.mobileCalendarControl.value) {
                this.mobileCalendarControl.setValue(null, { emitEvent: false });
            }
        });

        // Create an effect to watch for changes in the space and occupancy signals
        effect(() => {
            this.space(); // Access the signal to track it
            this.occupancyMap(); // Access the occupancy map to track it
            this.myReservationsMap(); // Access the my reservations map to track it
            this.sharedOccupancyMap(); // Access the shared occupancy map to track it
            this.updateCellAvailability();
        });
    }

    ngAfterViewInit(): void {
        // Set up a MutationObserver to detect month changes in the calendar
        if (this.calendarRef) {
            const observer = new MutationObserver(() => {
                this.detectMonthChange();
                // Update cell availability when calendar DOM changes
                this.updateCellAvailability();
            });

            observer.observe(this.calendarRef.nativeElement, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['class', 'data-month']
            });
        }

        // Initial call to update cell availability after view is initialized
        setTimeout(() => {
            this.updateCellAvailability();
        }, 100);
    }

    private detectMonthChange(): void {
        // Look for month indicators in the calendar DOM
        const monthElements = document.querySelectorAll('[data-month], .t-month, .tui-calendar__month');
        if (monthElements.length > 0) {
            const currentMonth = monthElements[0].textContent || monthElements[0].getAttribute('data-month') || '';
            if (currentMonth && currentMonth !== this.lastDetectedMonth) {
                this.lastDetectedMonth = currentMonth;
                // Try to parse the month and emit the change
                const monthDate = this.parseMonthFromText(currentMonth);
                if (monthDate) {
                    this.monthChanged.emit(monthDate);
                }
            }
        }
    }

    private parseMonthFromText(monthText: string): Date | null {
        // This is a simplified parser - you might need to adjust based on the actual format
        const now = new Date();
        const currentYear = now.getFullYear();

        // Try to extract month and year from the text
        const monthMatch = monthText.match(/(\d{1,2})\/(\d{4})/);
        if (monthMatch) {
            const month = parseInt(monthMatch[1]) - 1; // JavaScript months are 0-based
            const year = parseInt(monthMatch[2]);
            return new Date(year, month, 1);
        }

        return null;
    }


    updateCellAvailability() {
        // Use requestAnimationFrame to ensure DOM is ready
        requestAnimationFrame(() => {
            // Use the calendar wrapper element to scope the query
            const calendarElement = this.calendarRef?.nativeElement || document;
            const cells = calendarElement.querySelectorAll('.t-cell');

            if (cells.length === 0) {
                // If no cells found, try again after a short delay
                setTimeout(() => this.updateCellAvailability(), 50);
                return;
            }

            cells.forEach((cell: Element) => {
                const dot = cell.querySelector('.t-dot');
                if (dot) {
                    cell.classList.remove('not-available');
                    if (this.maxLength.day === 1) {
                        cell.classList.remove('t-cell_disabled');
                    }
                    cell.classList.remove('my-booking');
                    cell.classList.remove('shared-occupied');
                    cell.removeAttribute('data-type');
                    cell.removeAttribute('data-marker');

                    const dotColor = window.getComputedStyle(dot).backgroundColor;
                    if (dotColor == BEFORE_TODAY) {
                        cell.classList.add('t-cell_disabled');
                    } else if (dotColor == BOOKED_BY_ME) {
                        cell.classList.add('my-booking');
                        cell.classList.add('t-cell_disabled');
                        cell.setAttribute('data-type', 'my-booking');
                        cell.setAttribute('data-marker', 'my-booking');
                    } else if (dotColor == BOOKED) {
                        cell.classList.add('not-available');
                        cell.classList.add('t-cell_disabled');
                        cell.setAttribute('data-type', 'occupied');
                        cell.setAttribute('data-marker', 'occupied');
                    } else if (dotColor == SHARED_OCCUPIED) {
                        cell.classList.add('shared-occupied');
                        cell.classList.add('t-cell_disabled');
                        cell.setAttribute('data-type', 'shared-occupied');
                        cell.setAttribute('data-marker', 'shared-occupied');
                    }
                }
            });

            // Special handling for today's cell
            const todayCell = calendarElement.querySelector('.t-cell_today');
            if (todayCell) {
                // Check if today is occupied using the occupancy maps
                const today = new Date();
                const todayString = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
                const occupancyMap = this.occupancyMap();
                const myReservationsMap = this.myReservationsMap();
                const sharedOccupancyMap = this.sharedOccupancyMap();

                // Check if today is the current user's reservation first
                if (myReservationsMap.has(todayString)) {
                    // Today is occupied by current user's reservation
                    todayCell.classList.add('my-booking');
                    todayCell.classList.add('t-cell_disabled');
                    todayCell.setAttribute('data-type', 'my-booking');
                    todayCell.setAttribute('data-marker', 'my-booking');
                } else if (occupancyMap.has(todayString) && occupancyMap.get(todayString)) {
                    // Today is occupied by this space (but not by current user)
                    todayCell.classList.add('not-available');
                    todayCell.classList.add('t-cell_disabled');
                    todayCell.setAttribute('data-type', 'occupied');
                    todayCell.setAttribute('data-marker', 'occupied');
                } else if (sharedOccupancyMap.has(todayString) && sharedOccupancyMap.get(todayString)) {
                    // Today is occupied by a shared space
                    todayCell.classList.add('shared-occupied');
                    todayCell.classList.add('t-cell_disabled');
                    todayCell.setAttribute('data-type', 'shared-occupied');
                    todayCell.setAttribute('data-marker', 'shared-occupied');
                }
            }
        });
    }

    get markerHandler() {
        return (day: TuiDay): [string] => {
            this.updateCellAvailability();
            if (day.dayBefore(this.min)) {
                return [BEFORE_TODAY];
            }

            // Check if day is occupied using real occupancy data
            const dayString = `${day.year}-${String(day.month + 1).padStart(2, '0')}-${String(day.day).padStart(2, '0')}`;
            const occupancyMap = this.occupancyMap();
            const myReservationsMap = this.myReservationsMap();
            const sharedOccupancyMap = this.sharedOccupancyMap();

            // Check if this is the current user's reservation first
            if (myReservationsMap.has(dayString)) {
                // Day is occupied by current user's reservation
                return [BOOKED_BY_ME];
            }

            if (occupancyMap.has(dayString) && occupancyMap.get(dayString)) {
                // Day is occupied by this space (but not by current user)
                return [BOOKED];
            }

            if (sharedOccupancyMap.has(dayString) && sharedOccupancyMap.get(dayString)) {
                // Day is occupied by a shared space
                return [SHARED_OCCUPIED];
            }

            return [AVAILABLE]; // Available day
        };
    }

    public onRangeChange(range: TuiDayRange | null): void {
        if (range) {
            this.selectedDate.emit(range);
        }
    }

    protected get empty(): boolean {
        return !this.control.value;
    }

    protected onMobileCalendarChange(value: TuiDay | TuiDayRange | readonly TuiDay[] | null): void {
        // tui-mobile-calendar in range mode can emit different types
        if (value && 'from' in value && 'to' in value) {
            // Already a TuiDayRange
            const range = value as TuiDayRange;
            this.mobileCalendarControl.setValue(range, { emitEvent: false });
            this.control.setValue(range);
            this.selectedDate.emit(range);
        } else if (Array.isArray(value) && value.length === 2) {
            // Array of two TuiDay - create range
            const range = new TuiDayRange(value[0], value[1]);
            this.mobileCalendarControl.setValue(range, { emitEvent: false });
            this.control.setValue(range);
            this.selectedDate.emit(range);
        } else if (value && !Array.isArray(value) && !('from' in value)) {
            // Single TuiDay - convert to range (same day for start and end)
            const day = value as TuiDay;
            const range = new TuiDayRange(day, day);
            this.mobileCalendarControl.setValue(range, { emitEvent: false });
            this.control.setValue(range);
            this.selectedDate.emit(range);
        } else if (value === null) {
            // Clear selection
            this.mobileCalendarControl.setValue(null, { emitEvent: false });
            this.control.setValue(null);
            this.selectedDate.emit(null);
        }
    }

    get disabledItemHandler(): TuiBooleanHandler<TuiDay> {
        return (day: TuiDay) => {
            if (day.dayBefore(this.min)) {
                return true;
            }

            // Check if day is occupied using real occupancy data
            const dayString = `${day.year}-${String(day.month + 1).padStart(2, '0')}-${String(day.day).padStart(2, '0')}`;
            const occupancyMap = this.occupancyMap();
            const myReservationsMap = this.myReservationsMap();
            const sharedOccupancyMap = this.sharedOccupancyMap();

            // Check if this is the current user's reservation
            if (myReservationsMap.has(dayString)) {
                return true; // Day is occupied by current user's reservation (should be disabled)
            }

            if (occupancyMap.has(dayString) && occupancyMap.get(dayString)) {
                return true; // Day is occupied by this space
            }

            if (sharedOccupancyMap.has(dayString) && sharedOccupancyMap.get(dayString)) {
                return true; // Day is occupied by a shared space
            }

            return false; // Day is available
        };
    }

    onMonthChange(month: Date): void {
        this.monthChanged.emit(month);
    }
}
