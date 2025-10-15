import { CommonModule, ViewportScroller } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiDay, TuiDayRange } from '@taiga-ui/cdk';
import { TuiAlertService, TuiButton, TuiDataList, TuiHint, TuiIcon, TuiLoader, TuiTextfield, TuiTextfieldDropdownDirective } from '@taiga-ui/core';
import { TuiCalendarRange, TuiChevron, TuiDataListWrapper, TuiInputDateRange, TuiPagination, TuiSelect } from '@taiga-ui/kit';

import { SpaceCardComponent } from '../../../../components/space-card/space-card.component';

import { inject } from '@angular/core';
import { AUTH_SERVICE_TOKEN } from '../../../../global.provider';
import { UIUser } from '../../../../models/UIUser';
import { Space } from '../../services/spaces.service';
import { SPACES_SERVICE_TOKEN } from '../../spaces.provider';

@Component({
    selector: 'app-spaces-list',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        TranslateModule,
        TuiButton,
        TuiTextfield,
        TuiTextfieldDropdownDirective,
        TuiSelect,
        TuiChevron,
        TuiDataList,
        TuiDataListWrapper,
        TuiInputDateRange,
        TuiCalendarRange,
        TuiHint,
        TuiIcon,
        TuiLoader,
        TuiPagination,
        SpaceCardComponent
    ],
    templateUrl: './spaces-list.component.html',
    styleUrls: ['./spaces-list.component.scss']
})
export class SpacesListComponent implements OnInit {
    private spacesService = inject(SPACES_SERVICE_TOKEN);
    private authService = inject(AUTH_SERVICE_TOKEN);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private translate = inject(TranslateService);
    private alertService = inject(TuiAlertService);
    private viewportScroller = inject(ViewportScroller);

    // Signals for reactive state
    spaces = signal<Space[]>([]);
    currentUser = signal<UIUser | undefined>(undefined);
    loading = signal(false);
    searchQuery = signal('');
    selectedType = signal<any>(null);
    showAvailableOnly = signal(false);
    dateRange = signal<TuiDayRange | null>(null);

    // Pagination signals
    currentPage = signal(1);
    itemsPerPage = signal(12);
    totalItems = signal(0);
    totalPages = signal(0);

    // Filter options
    spaceTypes = [
        { value: '', label: 'spaces.list.allTypes' },
        { value: 'GUEST_ROOM', label: 'spaces.types.GUEST_ROOM' },
        { value: 'COMMON_ROOM', label: 'spaces.types.COMMON_ROOM' },
        { value: 'COWORKING', label: 'spaces.types.COWORKING' },
        { value: 'PARKING', label: 'spaces.types.PARKING' }
    ];

    stringifyType = (item: any): string => {
        if (!item || !item.label) return '';
        return this.translate.instant(item.label);
    };

    ngOnInit(): void {
        this.loadCurrentUser();
        this.loadSpaces();
        this.setupQueryParams();
    }

    private loadCurrentUser(): void {
        this.authService.getUserProfile().subscribe({
            next: (user: UIUser) => {
                this.currentUser.set(user);
            },
            error: (error: any) => {
                console.error('Error loading current user:', error);
            }
        });
    }

    private setupQueryParams(): void {
        this.route.queryParams.subscribe(params => {
            if (params['search']) {
                this.searchQuery.set(params['search']);
            }
            if (params['type']) {
                // Convert string value to object
                const typeObj = this.spaceTypes.find(t => t.value === params['type']);
                this.selectedType.set(typeObj || null);
            }
            if (params['available'] === 'true') {
                this.showAvailableOnly.set(true);
            }
            if (params['startDate'] && params['endDate']) {
                const from = new Date(params['startDate']);
                const to = new Date(params['endDate']);
                this.dateRange.set(new TuiDayRange(
                    new TuiDay(from.getFullYear(), from.getMonth(), from.getDate()),
                    new TuiDay(to.getFullYear(), to.getMonth(), to.getDate())
                ));
            }
            if (params['page']) {
                this.currentPage.set(parseInt(params['page'], 10));
            }
        });
    }

    private loadSpaces(): void {
        this.loading.set(true);

        const dateRange = this.dateRange();
        const startDate = dateRange?.from ? this.tuiDayToDate(dateRange.from).toISOString().split('T')[0] : undefined;
        const endDate = dateRange?.to ? this.tuiDayToDate(dateRange.to).toISOString().split('T')[0] : undefined;

        // Extract the value from the selected type object
        const typeValue = this.selectedType()?.value || undefined;

        this.spacesService.getSpaces(
            typeValue,
            this.showAvailableOnly() || undefined,
            startDate,
            endDate,
            this.searchQuery() || undefined,
            this.currentPage() - 1, // Convert to 0-based index for API
            this.itemsPerPage()
        ).subscribe({
            next: (response) => {
                this.spaces.set(response.content);
                this.totalItems.set(response.totalElements);
                this.totalPages.set(response.totalPages);
                this.loading.set(false);
            },
            error: (error) => {
                console.error('Error loading spaces:', error);
                this.alertService.open(
                    this.translate.instant('spaces.list.loadError'),
                    { appearance: 'negative' }
                ).subscribe();
                this.loading.set(false);
            }
        });
    }

    onSearchChange(query: string): void {
        this.searchQuery.set(query);
        this.currentPage.set(1); // Reset to first page
        this.updateUrl();
        this.loadSpaces();
    }

    onTypeChange(selected: any): void {
        // Store the entire object, not just the value
        this.selectedType.set(selected || null);
        this.currentPage.set(1); // Reset to first page
        this.updateUrl();
        this.loadSpaces();
    }

    onAvailableToggle(): void {
        this.showAvailableOnly.set(!this.showAvailableOnly());
        this.currentPage.set(1); // Reset to first page
        this.updateUrl();
        this.loadSpaces();
    }

    onDateRangeChange(dateRange: TuiDayRange | null): void {
        this.dateRange.set(dateRange);
        this.currentPage.set(1); // Reset to first page
        this.updateUrl();
        this.loadSpaces();
    }

    onClearFilters(): void {
        this.searchQuery.set('');
        this.selectedType.set(null);
        this.showAvailableOnly.set(false);
        this.dateRange.set(null);
        this.currentPage.set(1); // Reset to first page
        this.updateUrl();
        this.loadSpaces();
    }

    onPageChange(page: number): void {
        this.currentPage.set(page);
        this.updateUrl();
        this.loadSpaces();
    }

    private updateUrl(): void {
        const queryParams: any = {};

        if (this.searchQuery()) {
            queryParams.search = this.searchQuery();
        }
        // Extract the value from the selected type object
        if (this.selectedType()?.value) {
            queryParams.type = this.selectedType().value;
        }
        if (this.showAvailableOnly()) {
            queryParams.available = 'true';
        }
        if (this.dateRange()?.from && this.dateRange()?.to) {
            queryParams.startDate = this.tuiDayToDate(this.dateRange()!.from).toISOString().split('T')[0];
            queryParams.endDate = this.tuiDayToDate(this.dateRange()!.to).toISOString().split('T')[0];
        }
        if (this.currentPage() > 1) {
            queryParams.page = this.currentPage();
        }

        this.router.navigate([], {
            relativeTo: this.route,
            queryParams,
            queryParamsHandling: 'merge'
        });
    }


    onSpaceClick(space: Space): void {
        this.router.navigate(['/spaces/detail', space.id]).then(() => {
            this.viewportScroller.scrollToPosition([0, 0]);
        });
    }

    onReserveClick(space: Space): void {
        this.router.navigate(['/spaces/detail', space.id], {
            queryParams: { action: 'reserve' }
        }).then(() => {
            this.viewportScroller.scrollToPosition([0, 0]);
        });
    }

    getSpaceTypeLabel(type: any): string {
        // Handle both string values and objects
        if (typeof type === 'object' && type?.label) {
            return this.translate.instant(type.label);
        }
        const spaceType = this.spaceTypes.find(t => t.value === type);
        return spaceType ? this.translate.instant(spaceType.label) : type;
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

    formatDateRange(dateRange: TuiDayRange | null): string {
        if (!dateRange?.from || !dateRange?.to) return '';

        const from = this.tuiDayToDate(dateRange.from).toLocaleDateString('fr-FR');
        const to = this.tuiDayToDate(dateRange.to).toLocaleDateString('fr-FR');

        return `${from} - ${to}`;
    }

    private tuiDayToDate(day: TuiDay): Date {
        return new Date(day.year, day.month, day.day);
    }

    onFavoriteClick(space: Space): void {
        // TODO: Implement favorite functionality
        console.log('Toggle favorite for space:', space.id);
    }
}
