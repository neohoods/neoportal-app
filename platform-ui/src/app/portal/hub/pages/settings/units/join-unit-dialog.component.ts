import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, Input, OnInit, Optional, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiDialogContext, TuiIcon, TuiLabel, TuiLoader, TuiTextfield } from '@taiga-ui/core';
import { TuiChip, TuiDataListWrapper, TuiPagination } from '@taiga-ui/kit';
import { TuiInputModule, TuiSelectModule } from '@taiga-ui/legacy';
import { POLYMORPHEUS_CONTEXT } from '@taiga-ui/polymorpheus';
import { UnitsHubApiService } from '../../../../../api-client/api/unitsHubApi.service';
import { Unit } from '../../../../../api-client/model/unit';
import { AUTH_SERVICE_TOKEN } from '../../../../../global.provider';
import { AuthService } from '../../../../../services/auth.service';
import { UNITS_SERVICE_TOKEN } from '../../../hub.provider';
import { UnitsService } from '../../../services/units.service';

type UnitType = 'FLAT' | 'GARAGE' | 'PARKING' | 'COMMERCIAL' | 'OTHER' | null;
type PaginatedUnits = any;

@Component({
    selector: 'app-join-unit-dialog',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        TranslateModule,
        TuiButton,
        TuiIcon,
        TuiChip,
        TuiLabel,
        TuiLoader,
        TuiTextfield,
        TuiSelectModule,
        TuiDataListWrapper,
        TuiInputModule,
        TuiPagination
    ],
    templateUrl: './join-unit-dialog.component.html',
    styleUrl: './join-unit-dialog.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class JoinUnitDialogComponent implements OnInit {
    availableUnits = signal<Unit[]>([]);
    pendingRequests: Map<string, any> = new Map();
    loadingAvailable = signal(false);
    totalPages = signal(0);
    currentPage = signal(0);
    pageSize = signal(20);
    totalElements = signal(0);

    // Filters
    selectedType = signal<UnitType | null>(null);
    searchTerm = signal<string>('');

    // Options
    typeOptions = [
        { value: null, label: 'directory.filters.type.all' },
        { value: 'FLAT' as UnitType, label: 'units.types.FLAT' },
        { value: 'GARAGE' as UnitType, label: 'units.types.GARAGE' },
        { value: 'PARKING' as UnitType, label: 'units.types.PARKING' },
        { value: 'COMMERCIAL' as UnitType, label: 'units.types.COMMERCIAL' },
        { value: 'OTHER' as UnitType, label: 'units.types.OTHER' },
    ] as const;

    @Input() observer: any = null;

    stringifyType = (item: any): string => {
        if (!item) return '';
        if (item.label) {
            return this.translate.instant(item.label);
        }
        const option = this.typeOptions.find(opt => opt.value === item);
        if (option) {
            return this.translate.instant(option.label);
        }
        return '';
    };

    getSelectedTypeOption(): any {
        const currentType = this.selectedType();
        if (currentType === null) {
            return this.typeOptions[0];
        }
        return this.typeOptions.find(opt => opt.value === currentType) || this.typeOptions[0];
    }

    onTypeChange(selected: any): void {
        let type: UnitType | null = null;
        if (selected) {
            if (selected.value !== undefined) {
                type = selected.value;
            } else if (typeof selected === 'string') {
                type = selected as UnitType;
            }
        }
        this.selectedType.set(type);
        this.currentPage.set(0);
        this.loadAvailableUnits();
    }

    onSearchChange(): void {
        this.currentPage.set(0);
        this.loadAvailableUnits();
    }

    onPageChange(page: number): void {
        this.currentPage.set(page);
        this.loadAvailableUnits();
    }

    constructor(
        @Inject(UNITS_SERVICE_TOKEN) private unitsService: UnitsService,
        private unitsApiService: UnitsHubApiService,
        @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
        private translate: TranslateService,
        private alerts: TuiAlertService,
        private cdr: ChangeDetectorRef,
        @Optional() @Inject(POLYMORPHEUS_CONTEXT) private readonly context?: TuiDialogContext<void>
    ) { }

    ngOnInit(): void {
        this.loadPendingRequests();
        this.loadAvailableUnits();
    }

    loadPendingRequests(): void {
        this.unitsService.getMyJoinRequests().subscribe({
            next: (requests: any[]) => {
                this.pendingRequests.clear();
                requests.forEach(request => {
                    if (request.unitId) {
                        this.pendingRequests.set(request.unitId, request);
                    }
                });
                this.cdr.markForCheck();
            },
            error: () => {
                // Ignore errors
            }
        });
    }

    loadAvailableUnits(): void {
        this.loadingAvailable.set(true);
        this.cdr.markForCheck();

        // Get user's current units to filter them out
        this.unitsApiService.getUnits().subscribe({
            next: (userUnits: Unit[]) => {
                const userUnitIds = new Set(userUnits.map(u => u.id));

                // Load units from directory with filters and pagination
                const typeFilter = this.selectedType();
                this.unitsService.getUnitsDirectory(
                    this.currentPage(),
                    this.pageSize(),
                    typeFilter as any, // Cast to allow COMMERCIAL and OTHER
                    this.searchTerm() || undefined,
                    undefined // Don't filter by user in join dialog
                ).subscribe({
                    next: (paginated: PaginatedUnits) => {
                        const allUnits = paginated.content || [];
                        // Filter out units user is already a member of
                        const filteredUnits = allUnits.filter((unit: Unit) =>
                            !userUnitIds.has(unit.id)
                        );
                        this.availableUnits.set(filteredUnits);
                        this.totalPages.set(paginated.totalPages || 0);
                        this.currentPage.set(paginated.number || 0);
                        this.totalElements.set(paginated.totalElements || 0);
                        this.loadingAvailable.set(false);
                        this.cdr.markForCheck();
                    },
                    error: (error: any) => {
                        console.error('Failed to load available units:', error);
                        this.loadingAvailable.set(false);
                        this.cdr.markForCheck();
                    }
                });
            },
            error: () => {
                // If we can't get user units, just load directory without filtering
                const typeFilter = this.selectedType();
                this.unitsService.getUnitsDirectory(
                    this.currentPage(),
                    this.pageSize(),
                    typeFilter as any, // Cast to allow COMMERCIAL and OTHER
                    this.searchTerm() || undefined,
                    undefined
                ).subscribe({
                    next: (paginated: PaginatedUnits) => {
                        this.availableUnits.set(paginated.content || []);
                        this.totalPages.set(paginated.totalPages || 0);
                        this.currentPage.set(paginated.number || 0);
                        this.totalElements.set(paginated.totalElements || 0);
                        this.loadingAvailable.set(false);
                        this.cdr.markForCheck();
                    },
                    error: (error: any) => {
                        console.error('Failed to load available units:', error);
                        this.loadingAvailable.set(false);
                        this.cdr.markForCheck();
                    }
                });
            }
        });
    }

    requestToJoinUnit(unitId: string): void {
        this.unitsService.createJoinRequest(unitId).subscribe({
            next: (response: any) => {
                // Check if user was added directly (unit was empty)
                if (response && response.status === 'APPROVED') {
                    this.alerts.open(
                        this.translate.instant('settings.units.joinRequests.userAddedDirectly'),
                        { appearance: 'positive' }
                    ).subscribe();
                    // Reload available units
                    this.loadAvailableUnits();
                } else {
                    // Request was created
                    this.pendingRequests.set(unitId, response);
                    this.alerts.open(
                        this.translate.instant('settings.units.joinRequests.requestCreated'),
                        { appearance: 'positive' }
                    ).subscribe();
                    // Reload to update the list
                    this.loadAvailableUnits();
                }
            },
            error: (error: any) => {
                console.error('Failed to create join request:', error);
                this.alerts.open(
                    this.translate.instant('settings.units.joinRequests.requestCreated') + ': ' + (error.message || 'Erreur'),
                    { appearance: 'negative' }
                ).subscribe();
            }
        });
    }

    hasPendingRequest(unitId: string | null | undefined): boolean {
        if (!unitId) return false;
        return this.pendingRequests.has(unitId);
    }

    closeDialog(): void {
        if (this.observer) {
            this.observer.complete();
        } else if (this.context) {
            this.context.completeWith(undefined);
        }
    }
}

