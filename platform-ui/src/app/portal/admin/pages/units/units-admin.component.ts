import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiDialogService, TuiIcon } from '@taiga-ui/core';
import { TUI_CONFIRM, TuiConfirmData } from '@taiga-ui/kit';
import { map, Observable } from 'rxjs';
import { PaginatedUnits } from '../../../../api-client/model/paginatedUnits';
import { Unit } from '../../../../api-client/model/unit';
import { UNITS_SERVICE_TOKEN } from '../../admin.providers';
import { Column, ItemPagination, TosTableComponent } from '../../components/tos-table/tos-table.component';
import { UnitsService } from '../../services/units.service';

@Component({
    standalone: true,
    imports: [
        TuiButton,
        TuiIcon,
        RouterModule,
        TranslateModule,
        DatePipe,
        TosTableComponent
    ],
    selector: 'app-units-admin',
    templateUrl: './units-admin.component.html',
    styleUrls: ['./units-admin.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UnitsAdminComponent implements OnInit {
    protected units: Unit[] = [];
    protected totalItems = 0;
    protected currentPage = 0;
    protected pageSize = 10;
    protected searchTerm = '';


    columns: Column[] = [];

    getDataFunction = (
        searchText?: string,
        sortBy?: string,
        sortOrder?: 'asc' | 'desc',
        page?: number,
        pageSize?: number
    ): Observable<ItemPagination> => {
        const currentPage = page ?? 0;
        const size = pageSize ?? 10;
        const search = searchText || '';

        this.currentPage = currentPage;
        this.pageSize = size;
        this.searchTerm = search;

        return this.unitsService.getUnits(currentPage, size, search).pipe(
            map((paginated: PaginatedUnits) => {
                this.units = paginated.content || [];
                this.totalItems = paginated.totalElements || 0;

                return {
                    items: paginated.content || [],
                    totalItems: paginated.totalElements || 0,
                    totalPages: paginated.totalPages || 0,
                    currentPage: paginated.number || 0,
                    itemsPerPage: paginated.size || size,
                } as ItemPagination;
            })
        );
    };

    constructor(
        @Inject(UNITS_SERVICE_TOKEN) private unitsService: UnitsService,
        private translate: TranslateService,
        private alerts: TuiAlertService,
        private dialogs: TuiDialogService,
        private router: Router,
        private cdr: ChangeDetectorRef
    ) {
        this.columns = [
            {
                key: 'name',
                label: this.translate.instant('units.columns.name'),
                visible: true,
                sortable: true,
                size: 'm',
            },
            {
                key: 'membersCount',
                label: this.translate.instant('units.columns.membersCount'),
                visible: true,
                sortable: false,
                size: 's',
                custom: true,
            },
            {
                key: 'createdAt',
                label: this.translate.instant('units.columns.createdAt'),
                visible: true,
                sortable: true,
                custom: true,
                size: 'm',
            },
        ];
    }

    ngOnInit(): void {
        // Data will be loaded by TosTableComponent via getDataFunction
    }

    deleteUnit(unit: Unit): void {
        const data: TuiConfirmData = {
            content: this.translate.instant('units.confirmDelete', { name: unit.name }),
            yes: this.translate.instant('common.yes'),
            no: this.translate.instant('common.no'),
        };

        this.dialogs
            .open<boolean>(TUI_CONFIRM, {
                label: this.translate.instant('units.deleteUnit', { name: unit.name }),
                size: 'm',
                data,
            })
            .subscribe((response) => {
                if (response) {
                    this.unitsService.deleteUnit(unit.id!).subscribe({
                        next: () => {
                            this.alerts.open(this.translate.instant('units.deleted', { name: unit.name })).subscribe();
                            // Reload data - getDataFunction signature: (searchText?, sortBy?, sortOrder?, page?, pageSize?)
                            this.getDataFunction(this.searchTerm, undefined, undefined, this.currentPage, this.pageSize);
                        },
                        error: (error: any) => {
                            console.error('Failed to delete unit:', error);
                            this.alerts.open(this.translate.instant('units.deleteError')).subscribe();
                        }
                    });
                }
            });
    }

    viewUnit(unitId: string): void {
        this.router.navigate(['/admin/units', unitId]);
    }
}

