import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiLoader, TuiTextfield, TuiTextfieldDropdownDirective } from '@taiga-ui/core';
import { TuiChevron, TuiDataListWrapper, TuiSelect, TuiTabs } from '@taiga-ui/kit';

import { UIAccessCode, UIDigitalLock } from '../../../../models/UIDigitalLock';
import { Space } from '../../../spaces/services/spaces.service';
import { ADMIN_SPACES_SERVICE_TOKEN } from '../../admin.providers';
import { DIGITAL_LOCK_SERVICE_TOKEN } from '../../services/digital-lock.service';
import { NukiDigitalLockCardComponent } from './components/nuki-digital-lock-card.component';
import { TtlockDigitalLockCardComponent } from './components/ttlock-digital-lock-card.component';

@Component({
    selector: 'app-digital-lock-admin',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        TuiButton,
        TuiIcon,
        TuiLoader,
        TuiTextfield,
        TuiTextfieldDropdownDirective,
        TuiSelect,
        TuiChevron,
        TuiTabs,
        TuiDataListWrapper,
        TranslateModule,
        TtlockDigitalLockCardComponent,
        NukiDigitalLockCardComponent
    ],
    templateUrl: './digital-lock-admin.component.html',
    styleUrls: ['./digital-lock-admin.component.scss']
})
export class DigitalLockAdminComponent implements OnInit {
    private router = inject(Router);
    private translate = inject(TranslateService);
    private alertService = inject(TuiAlertService);
    private digitalLockService = inject(DIGITAL_LOCK_SERVICE_TOKEN);
    private spacesService = inject(ADMIN_SPACES_SERVICE_TOKEN);

    digitalLocks = signal<UIDigitalLock[]>([]);
    spaces = signal<Space[]>([]);
    loading = signal(false);
    activeTab = signal(0);
    searchQuery = signal('');
    statusFilter = signal('');
    typeFilter = signal('');

    statusOptions = [
        { value: '', label: 'digitalLock.admin.allStatuses' },
        { value: 'ACTIVE', label: 'digitalLock.admin.status.ACTIVE' },
        { value: 'INACTIVE', label: 'digitalLock.admin.status.INACTIVE' },
        { value: 'ERROR', label: 'digitalLock.admin.status.ERROR' }
    ];

    typeOptions = [
        { value: '', label: 'digitalLock.admin.allTypes' },
        { value: 'TTLOCK', label: 'digitalLock.admin.types.TTLOCK' },
        { value: 'NUKI', label: 'digitalLock.admin.types.NUKI' },
        { value: 'YALE', label: 'digitalLock.admin.types.YALE' }
    ];

    stringifyStatus = (item: any): string => {
        if (!item || !item.label) return '';
        return this.translate.instant(item.label);
    };

    stringifyType = (item: any): string => {
        if (!item || !item.label) return '';
        return this.translate.instant(item.label);
    };

    ngOnInit(): void {
        this.loadDigitalLocks();
        this.loadSpaces();
    }

    private loadSpaces(): void {
        this.spacesService.getSpaces(0, 1000).subscribe({
            next: (response) => {
                this.spaces.set(response.content);
            },
            error: (error) => {
                console.error('Error loading spaces:', error);
                this.alertService.open(
                    this.translate.instant('spaces.admin.loadError'),
                    { appearance: 'negative' }
                ).subscribe();
            }
        });
    }

    private loadDigitalLocks(): void {
        this.loading.set(true);

        this.digitalLockService.getDigitalLocks().subscribe({
            next: (locks) => {
                this.digitalLocks.set(locks);
                this.loading.set(false);
            },
            error: (error) => {
                console.error('Error loading digital locks:', error);
                this.loading.set(false);
                this.alertService.open(
                    this.translate.instant('digitalLock.admin.errorLoadingLocks') || 'Error loading digital locks',
                    { appearance: 'error' }
                ).subscribe();
            }
        });
    }

    filteredDigitalLocks = computed(() => {
        let filtered = this.digitalLocks();

        // Search filter
        if (this.searchQuery()) {
            const query = this.searchQuery().toLowerCase();
            filtered = filtered.filter(lock => {
                const nameMatch = lock.name.toLowerCase().includes(query);
                const deviceIdMatch = (lock.ttlockConfig?.deviceId || lock.nukiConfig?.deviceId || '').toLowerCase().includes(query);
                const locationMatch = (lock.ttlockConfig?.location || '').toLowerCase().includes(query);
                return nameMatch || deviceIdMatch || locationMatch;
            });
        }

        // Status filter
        if (this.statusFilter()) {
            filtered = filtered.filter(lock => lock.status === this.statusFilter());
        }

        // Type filter
        if (this.typeFilter()) {
            filtered = filtered.filter(lock => lock.type === this.typeFilter());
        }

        return filtered;
    });

    activeLocks = computed(() => {
        return this.digitalLocks().filter(lock => lock.status === 'ACTIVE');
    });

    inactiveLocks = computed(() => {
        return this.digitalLocks().filter(lock => lock.status === 'INACTIVE');
    });

    errorLocks = computed(() => {
        return this.digitalLocks().filter(lock => lock.status === 'ERROR');
    });

    onTabChange(index: number): void {
        this.activeTab.set(index);
    }

    getLocksForTab(): UIDigitalLock[] {
        const filtered = this.filteredDigitalLocks();

        switch (this.activeTab()) {
            case 0: return filtered; // All locks
            case 1: return filtered.filter(lock => lock.status === 'ACTIVE');
            case 2: return filtered.filter(lock => lock.status === 'INACTIVE');
            case 3: return filtered.filter(lock => lock.status === 'ERROR');
            default: return filtered;
        }
    }

    onSearchChange(value: string): void {
        this.searchQuery.set(value);
    }

    onStatusChange(status: any): void {
        const statusValue = status?.value || status || '';
        this.statusFilter.set(statusValue);
    }

    onTypeChange(type: any): void {
        const typeValue = type?.value || type || '';
        this.typeFilter.set(typeValue);
    }

    onClearFilters(): void {
        this.searchQuery.set('');
        this.statusFilter.set('');
        this.typeFilter.set('');
    }

    onAddLock(): void {
        this.router.navigate(['/admin/digital-locks/add']);
    }

    onEditLock(lockId: string): void {
        this.router.navigate(['/admin/digital-locks', lockId.toString(), 'edit']);
    }

    onGenerateCode(lock: UIDigitalLock): void {
        // Call the service to generate an access code
        this.digitalLockService.generateAccessCode(lock.id.toString(), 24, 'Code généré depuis l\'admin').subscribe({
            next: (accessCode: UIAccessCode) => {
                this.alertService.open(
                    this.translate.instant('digitalLock.admin.accessCodeGenerated') + ': ' + accessCode.code,
                    { appearance: 'success' }
                ).subscribe();
            },
            error: (error) => {
                console.error('Error generating access code:', error);
                this.alertService.open(
                    this.translate.instant('digitalLock.admin.errorGeneratingAccessCode'),
                    { appearance: 'error' }
                ).subscribe();
            }
        });
    }

    onToggleLock(lock: UIDigitalLock): void {
        const newStatus = lock.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';

        this.digitalLockService.toggleDigitalLockStatus(lock.id.toString()).subscribe({
            next: (updatedLock) => {
                // Update local state
                this.digitalLocks.update(locks =>
                    locks.map(l =>
                        l.id === lock.id
                            ? { ...l, status: newStatus as any, updatedAt: new Date().toISOString() }
                            : l
                    )
                );

                this.alertService.open(
                    this.translate.instant('digitalLock.admin.lockUpdated', {
                        name: lock.name,
                        status: this.translate.instant(`digitalLock.admin.status.${newStatus}`)
                    })
                ).subscribe();
            },
            error: (error) => {
                console.error('Error toggling lock:', error);
                this.alertService.open(
                    this.translate.instant('digitalLock.admin.errorUpdatingLock') || 'Error updating lock',
                    { appearance: 'error' }
                ).subscribe();
            }
        });
    }

    onDeleteLock(lock: UIDigitalLock): void {
        this.digitalLockService.deleteDigitalLock(lock.id.toString()).subscribe({
            next: () => {
                this.digitalLocks.update(locks => locks.filter(l => l.id !== lock.id));

                this.alertService.open(
                    this.translate.instant('digitalLock.admin.lockDeleted', { name: lock.name })
                ).subscribe();
            },
            error: (error) => {
                console.error('Error deleting lock:', error);
                this.alertService.open(
                    this.translate.instant('digitalLock.admin.errorDeletingLock') || 'Error deleting lock',
                    { appearance: 'error' }
                ).subscribe();
            }
        });
    }

    getStatusIcon(status: string): string {
        switch (status) {
            case 'ACTIVE': return '@tui.check-circle';
            case 'INACTIVE': return '@tui.pause-circle';
            case 'ERROR': return '@tui.alert-circle';
            default: return '@tui.help-circle';
        }
    }

    getStatusColor(status: string): string {
        switch (status) {
            case 'ACTIVE': return 'var(--tos-text-positive)';
            case 'INACTIVE': return 'var(--tos-text-secondary)';
            case 'ERROR': return 'var(--tos-text-negative)';
            default: return 'var(--tos-text-secondary)';
        }
    }

    formatLastSeen(lastSeen: string): string {
        const date = new Date(lastSeen);
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffMins = Math.floor(diffMs / 60000);

        if (diffMins < 1) return this.translate.instant('digitalLock.admin.justNow');
        if (diffMins < 60) return this.translate.instant('digitalLock.admin.minutesAgo', { minutes: diffMins });

        const diffHours = Math.floor(diffMins / 60);
        if (diffHours < 24) return this.translate.instant('digitalLock.admin.hoursAgo', { hours: diffHours });

        const diffDays = Math.floor(diffHours / 24);
        return this.translate.instant('digitalLock.admin.daysAgo', { days: diffDays });
    }

    getTranslatedSpaceType(type: string): string {
        const typeMap: { [key: string]: string } = {
            'GUEST_ROOM': 'spaces.spaceType.guestRoom',
            'COMMON_ROOM': 'spaces.spaceType.commonRoom',
            'COWORKING': 'spaces.spaceType.coworking',
            'PARKING': 'spaces.spaceType.parking'
        };
        return this.translate.instant(typeMap[type] || 'spaces.spaceType.unknown');
    }

    getTranslatedSpaceStatus(status: string): string {
        const statusMap: { [key: string]: string } = {
            'ACTIVE': 'spaces.admin.status.active',
            'INACTIVE': 'spaces.admin.status.inactive',
            'MAINTENANCE': 'spaces.admin.status.maintenance'
        };
        return this.translate.instant(statusMap[status] || 'spaces.admin.status.unknown');
    }

    getSpaceStatusIcon(status: string): string {
        switch (status) {
            case 'ACTIVE': return '@tui.check-circle';
            case 'INACTIVE': return '@tui.pause-circle';
            case 'MAINTENANCE': return '@tui.tool';
            default: return '@tui.help-circle';
        }
    }

    getSpaceStatusColor(status: string): string {
        switch (status) {
            case 'ACTIVE': return 'var(--tos-text-positive)';
            case 'INACTIVE': return 'var(--tos-text-secondary)';
            case 'MAINTENANCE': return 'var(--tos-text-warning)';
            default: return 'var(--tos-text-secondary)';
        }
    }

    getSpaceName(spaceId: string): string {
        const space = this.spaces().find(s => s.id === spaceId);
        return space ? space.name : 'Espace inconnu';
    }

    getSpaceType(spaceId: string): string {
        const space = this.spaces().find(s => s.id === spaceId);
        return space ? this.getTranslatedSpaceType(space.type) : 'N/A';
    }

    getSpaceStatus(spaceId: string): string {
        const space = this.spaces().find(s => s.id === spaceId);
        return space ? space.status : 'UNKNOWN';
    }
}
