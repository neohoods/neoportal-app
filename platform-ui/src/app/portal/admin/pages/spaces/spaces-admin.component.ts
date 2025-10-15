import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiLabel, TuiLoader, TuiTextfield, TuiTextfieldDropdownDirective } from '@taiga-ui/core';
import { TuiChevron, TuiDataListWrapper, TuiSelect } from '@taiga-ui/kit';
import { Space } from '../../../spaces/services/spaces.service';
import { ADMIN_SPACES_SERVICE_TOKEN } from '../../admin.providers';

@Component({
    selector: 'app-spaces-admin',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        TuiButton,
        TuiIcon,
        TuiLabel,
        TuiLoader,
        TuiTextfield,
        TuiTextfieldDropdownDirective,
        TuiSelect,
        TuiChevron,
        TuiDataListWrapper,
        TranslateModule
    ],
    templateUrl: './spaces-admin.component.html',
    styleUrls: ['./spaces-admin.component.scss']
})
export class SpacesAdminComponent implements OnInit {
    private spacesService = inject(ADMIN_SPACES_SERVICE_TOKEN);
    private router = inject(Router);
    private alertService = inject(TuiAlertService);
    private translate = inject(TranslateService);

    spaces = signal<Space[]>([]);
    filteredSpaces = signal<Space[]>([]);
    loading = signal(false);

    // Pagination
    currentPage = signal(1);
    itemsPerPage = signal(9);
    totalItems = signal(0);
    totalPages = signal(0);

    // Filtering
    selectedType = signal<any>(null);
    selectedStatus = signal<any>(null);

    spaceTypes = [
        { value: '', label: 'spaces.admin.allTypes' },
        { value: 'GUEST_ROOM', label: 'spaces.types.GUEST_ROOM' },
        { value: 'COMMON_ROOM', label: 'spaces.types.COMMON_ROOM' },
        { value: 'COWORKING', label: 'spaces.types.COWORKING' },
        { value: 'PARKING', label: 'spaces.types.PARKING' }
    ];

    spaceStatuses = [
        { value: '', label: 'spaces.admin.allStatuses' },
        { value: 'ACTIVE', label: 'spaces.status.ACTIVE' },
        { value: 'INACTIVE', label: 'spaces.status.INACTIVE' }
    ];

    stringifyType = (item: any): string => {
        if (!item || !item.label) return '';
        return this.translate.instant(item.label);
    };

    stringifyStatus = (item: any): string => {
        if (!item || !item.label) return '';
        return this.translate.instant(item.label);
    };

    ngOnInit(): void {
        this.loadSpaces();
    }

    private loadSpaces(): void {
        this.loading.set(true);

        const page = this.currentPage() - 1; // Convert to 0-based index
        const size = this.itemsPerPage();

        // Extract values from objects
        const typeValue = this.selectedType()?.value || undefined;
        const statusValue = this.selectedStatus()?.value || undefined;

        this.spacesService.getSpaces(page, size, typeValue, statusValue).subscribe({
            next: (response) => {
                this.spaces.set(response.content);
                this.filteredSpaces.set(response.content);
                this.totalItems.set(response.totalElements);
                this.totalPages.set(response.totalPages);
                this.loading.set(false);
            },
            error: (error) => {
                console.error('Error loading spaces:', error);
                this.alertService.open(
                    this.translate.instant('spaces.admin.loadError'),
                    { appearance: 'negative' }
                ).subscribe();
                this.loading.set(false);
            }
        });
    }


    onTypeChange(type: any): void {
        // If "all types" is selected (empty value), set to null
        if (type?.value === '') {
            this.selectedType.set(null);
        } else {
            this.selectedType.set(type || null);
        }
        this.currentPage.set(1); // Reset to first page
        this.loadSpaces();
    }

    onStatusChange(status: any): void {
        // If "all statuses" is selected (empty value), set to null
        if (status?.value === '') {
            this.selectedStatus.set(null);
        } else {
            this.selectedStatus.set(status || null);
        }
        this.currentPage.set(1); // Reset to first page
        this.loadSpaces();
    }

    onClearFilters(): void {
        this.selectedType.set(null);
        this.selectedStatus.set(null);
        this.currentPage.set(1); // Reset to first page
        this.loadSpaces();
    }

    onPageChange(page: number): void {
        this.currentPage.set(page);
        this.loadSpaces();
    }

    onViewSpace(space: Space): void {
        this.router.navigate(['/admin/spaces', space.id]);
    }

    onEditSpace(space: Space): void {
        this.router.navigate(['/admin/spaces', space.id, 'edit']);
    }

    onToggleSpaceStatus(space: Space): void {
        this.spacesService.toggleSpaceStatus(space.id).subscribe({
            next: (updatedSpace) => {
                const messageKey = updatedSpace.status === 'ACTIVE' ? 'spaces.admin.spaceActivated' : 'spaces.admin.spaceDeactivated';
                this.alertService.open(
                    this.translate.instant(messageKey)
                ).subscribe();

                // Reload spaces to get updated data
                this.loadSpaces();
            },
            error: (error) => {
                console.error('Error toggling space status:', error);
                this.alertService.open(
                    this.translate.instant('spaces.admin.errorUpdatingStatus')
                ).subscribe();
            }
        });
    }

    onViewReservations(space: Space): void {
        // Navigate to reservations page with space filter
        this.router.navigate(['/admin/reservations'], {
            queryParams: { spaceId: space.id }
        });
    }

    onAddSpace(): void {
        this.router.navigate(['/admin/spaces/add']);
    }

    getQuotaDescription(space: Space): string {
        if (!space.quota) return this.translate.instant('spaces.admin.unlimited');

        const max = space.quota.max || 0;

        if (max === 0) return this.translate.instant('spaces.admin.unlimited');

        // Show max capacity only
        return `${max} ${this.translate.instant('spaces.admin.reservations')} max`;
    }

    getSpaceTypeIcon(type: string): string {
        switch (type) {
            case 'GUEST_ROOM': return '@tui.home';
            case 'COMMON_ROOM': return '@tui.users';
            case 'COWORKING': return '@tui.monitor';
            case 'PARKING': return '@tui.car';
            default: return '@tui.help-circle';
        }
    }

    getSpaceTypeLabel(type: any): string {
        // Handle both string values and objects
        if (typeof type === 'object' && type?.label) {
            return this.translate.instant(type.label);
        }
        return this.translate.instant(`spaces.types.${type}`);
    }

    getSpaceStatusLabel(status: any): string {
        // Handle both string values and objects
        if (typeof status === 'object' && status?.label) {
            return this.translate.instant(status.label);
        }
        return this.translate.instant(`spaces.status.${status}`);
    }

    get paginatedSpaces(): Space[] {
        return this.filteredSpaces();
    }

    getEndIndex(): number {
        return Math.min(this.currentPage() * this.itemsPerPage(), this.filteredSpaces().length);
    }

    getPageNumbers(): number[] {
        const totalPages = this.totalPages();
        const current = this.currentPage();
        const pages: number[] = [];

        // Show max 5 pages around current page
        const start = Math.max(1, current - 2);
        const end = Math.min(totalPages, current + 2);

        for (let i = start; i <= end; i++) {
            pages.push(i);
        }

        return pages;
    }
}