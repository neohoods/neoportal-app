import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiDialogService, TuiIcon, TuiLabel, TuiTextfield, TuiTextfieldDropdownDirective } from '@taiga-ui/core';
import { type TuiStringMatcher } from '@taiga-ui/cdk';
import { TuiChevron, TuiComboBox, TuiDataListWrapper, TuiFilterByInputPipe, TuiPagination, TuiSelect, TuiTabs } from '@taiga-ui/kit';
import { Reservation } from '../../../../api-client/model/reservation';
import { ReservationStatus } from '../../../../api-client/model/reservationStatus';
import { SpaceType } from '../../../../api-client/model/spaceType';
import { UIUser } from '../../../../models/UIUser';
import { StatusChipComponent } from '../../../../shared/components/status-chip/status-chip.component';
import { ADMIN_RESERVATIONS_SERVICE_TOKEN, USERS_SERVICE_TOKEN } from '../../admin.providers';

@Component({
    selector: 'app-reservations-admin',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        TuiButton,
        TuiIcon,
        TuiLabel,
        TuiTextfield,
        TuiTextfieldDropdownDirective,
        TuiSelect,
        TuiComboBox,
        TuiFilterByInputPipe,
        TuiChevron,
        TuiDataListWrapper,
        TuiTabs,
        TuiPagination,
        TranslateModule,
        StatusChipComponent
    ],
    templateUrl: './reservations-admin.component.html',
    styleUrls: ['./reservations-admin.component.scss']
})
export class ReservationsAdminComponent implements OnInit {
    private reservationsService = inject(ADMIN_RESERVATIONS_SERVICE_TOKEN);
    private usersService = inject(USERS_SERVICE_TOKEN);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private alertService = inject(TuiAlertService);
    private dialogService = inject(TuiDialogService);
    private translate = inject(TranslateService);

    // Data properties
    reservations = signal<Reservation[]>([]);
    users = signal<Map<string, UIUser>>(new Map());
    loading = signal(false);

    // Count signals
    pastCount = signal(0);
    currentCount = signal(0);
    futureCount = signal(0);

    // Pagination
    currentPage = signal(1);
    itemsPerPage = signal(10);
    totalItems = signal(0);
    totalPages = signal(0);

    // Filtering
    availableUsers = signal<UIUser[]>([]);
    selectedUserId = signal<string | null>(null);
    selectedUser = signal<UIUser | null>(null);
    selectedSpaceType = signal('');

    // Tabs
    activeTab = signal(0); // 0: Past, 1: Current, 2: Future

    spaceTypes = [
        { value: '', label: 'reservations.admin.allSpaceTypes' },
        { value: SpaceType.GuestRoom, label: 'spaces.spaceType.guestRoom' },
        { value: SpaceType.CommonRoom, label: 'spaces.spaceType.commonRoom' },
        { value: SpaceType.Coworking, label: 'spaces.spaceType.coworking' },
        { value: SpaceType.Parking, label: 'spaces.spaceType.parking' }
    ];

    stringifySpaceType = (item: any): string => {
        if (!item || !item.label) return '';
        return this.translate.instant(item.label);
    };

    stringifyUser = (user: UIUser | null): string => {
        if (!user) return '';
        return `${user.firstName} ${user.lastName} (${user.email})`;
    };

    readonly userMatcher: TuiStringMatcher<UIUser> = (item, query) => {
        const fullName = `${item.firstName} ${item.lastName}`.toLowerCase();
        const email = item.email?.toLowerCase() || '';
        const searchQuery = query.toLowerCase();
        return fullName.includes(searchQuery) || email.includes(searchQuery);
    };

    ngOnInit(): void {
        this.loadAvailableUsers();
        this.loadReservations();
        this.loadCounts();
    }

    private loadAvailableUsers(): void {
        this.usersService.getUsers().subscribe({
            next: (users: UIUser[]) => {
                this.availableUsers.set(users);
            },
            error: (error) => {
                console.error('Error loading users:', error);
            }
        });
    }

    // Data loading
    private loadReservations(): void {
        this.loading.set(true);

        const spaceTypeValue = this.selectedSpaceType() || undefined;
        const userIdValue = this.selectedUserId() || undefined;
        const page = this.currentPage() - 1;
        const size = this.itemsPerPage();

        // Calculer les dates pour le filtrage selon l'onglet actif
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        let startDate: string | undefined;
        let endDate: string | undefined;

        switch (this.activeTab()) {
            case 0: // Passées
                // Pour les passées, on passe seulement endDate < aujourd'hui
                // Le backend filtrera selon endDate <= endDate
                endDate = today.toISOString().split('T')[0];
                break;
            case 1: // En cours
                startDate = today.toISOString().split('T')[0];
                endDate = today.toISOString().split('T')[0];
                break;
            case 2: // À venir
                // Pour les à venir, on passe seulement startDate > aujourd'hui
                // Le backend filtrera selon startDate >= startDate
                startDate = new Date(today.getTime() + 24 * 60 * 60 * 1000).toISOString().split('T')[0];
                break;
        }

        this.reservationsService.getReservations(page, size, undefined, spaceTypeValue, undefined, userIdValue).subscribe({
            next: (response) => {
                this.reservations.set(response.content);
                this.totalItems.set(response.totalElements);
                this.totalPages.set(response.totalPages);
                this.loadUsers(); // Load user information
                this.loading.set(false);
            },
            error: (error) => {
                console.error('Error loading reservations:', error);
                this.alertService.open(
                    this.translate.instant('reservations.admin.loadError'),
                    { appearance: 'negative' }
                ).subscribe();
                this.loading.set(false);
            }
        });
    }

    private loadCounts(): void {
        const spaceTypeValue = this.selectedSpaceType() || undefined;
        const userIdValue = this.selectedUserId() || undefined;

        // Pour les comptes, on charge toutes les réservations et on filtre côté client
        // car l'API ne supporte pas le filtrage par date
        this.reservationsService.getReservations(0, 1000, undefined, spaceTypeValue, undefined, userIdValue).subscribe({
            next: (response) => {
                const allReservations = response.content;
                const today = new Date();
                today.setHours(0, 0, 0, 0);

                // Filtrer par date
                const pastReservations = allReservations.filter(r => {
                    const endDate = new Date(r.endDate);
                    endDate.setHours(0, 0, 0, 0);
                    return endDate < today;
                });

                const currentReservations = allReservations.filter(r => {
                    const startDate = new Date(r.startDate);
                    const endDate = new Date(r.endDate);
                    startDate.setHours(0, 0, 0, 0);
                    endDate.setHours(0, 0, 0, 0);
                    return startDate <= today && endDate >= today;
                });

                const futureReservations = allReservations.filter(r => {
                    const startDate = new Date(r.startDate);
                    startDate.setHours(0, 0, 0, 0);
                    return startDate > today;
                });

                this.pastCount.set(pastReservations.length);
                this.currentCount.set(currentReservations.length);
                this.futureCount.set(futureReservations.length);
            },
            error: (error) => {
                console.error('Error loading counts:', error);
            }
        });
    }

    private loadUsers(): void {
        const userIds = [...new Set(this.reservations().map(r => r.userId))];
        if (userIds.length === 0) return;

        // Use batch loading instead of individual requests
        this.usersService.getUsersByIds(userIds).subscribe({
            next: (users: UIUser[]) => {
                const userMap = new Map<string, UIUser>();
                users.forEach(user => {
                    userMap.set(user.id, user);
                });
                this.users.set(userMap);
            },
            error: (error: any) => {
                console.error('Error loading users:', error);
            }
        });
    }

    // Filter methods
    onUserChange(user: UIUser | null): void {
        this.selectedUser.set(user);
        this.selectedUserId.set(user?.id || null);
        this.currentPage.set(1); // Reset to first page
        setTimeout(() => {
            this.loadReservations();
            this.loadCounts();
        }, 0);
    }

    onSpaceTypeChange(type: any): void {
        const spaceTypeValue = type?.value || type || '';
        this.selectedSpaceType.set(spaceTypeValue);
        this.currentPage.set(1); // Reset to first page
        // Use setTimeout to ensure signal is updated before loading
        setTimeout(() => {
            this.loadReservations();
            this.loadCounts();
        }, 0);
    }

    onClearFilters(): void {
        this.selectedUserId.set(null);
        this.selectedUser.set(null);
        this.selectedSpaceType.set('');
        this.currentPage.set(1); // Reset to first page
        // Use setTimeout to ensure signals are updated before loading
        setTimeout(() => {
            this.loadReservations();
            this.loadCounts();
        }, 0);
    }

    hasActiveFilters(): boolean {
        return (
            !!this.selectedUserId() ||
            !!this.selectedSpaceType()
        );
    }

    getActiveFiltersCount(): number {
        let count = 0;
        if (!!this.selectedUserId()) count++;
        if (!!this.selectedSpaceType()) count++;
        return count;
    }

    // Tab methods
    selectTab(index: number): void {
        this.activeTab.set(index);
        this.currentPage.set(1);
        this.loadReservations(); // Reload data for the new tab
    }

    getReservationsForTab(): Reservation[] {
        return this.reservations();
    }

    getPastReservationsCount(): number {
        return this.pastCount();
    }

    getCurrentReservationsCount(): number {
        return this.currentCount();
    }

    getFutureReservationsCount(): number {
        return this.futureCount();
    }

    // Pagination methods
    get paginatedReservations(): Reservation[] {
        return this.reservations();
    }

    goToPage(page: number): void {
        this.currentPage.set(page);
        this.loadReservations();
    }

    getStartIndex(): number {
        return (this.currentPage() - 1) * this.itemsPerPage() + 1;
    }

    getEndIndex(): number {
        const end = this.currentPage() * this.itemsPerPage();
        const total = this.totalItems();
        return end > total ? total : end;
    }

    // Helper methods for display
    getUserFullName(userId: string): string {
        const user = this.users().get(userId);
        return user ? `${user.firstName} ${user.lastName}` : 'N/A';
    }

    getTranslatedSpaceType(spaceType: SpaceType | string): string {
        if (!spaceType) return 'N/A';
        const option = this.spaceTypes.find(st => st.value === spaceType);
        return option ? this.translate.instant(option.label) : spaceType;
    }

    getSpaceTypeIcon(type: SpaceType | string | undefined): string {
        if (!type) return '@tui.help-circle';
        switch (type) {
            case SpaceType.GuestRoom: return '@tui.home';
            case SpaceType.CommonRoom: return '@tui.users';
            case SpaceType.Coworking: return '@tui.monitor';
            case SpaceType.Parking: return '@tui.car';
            default: return '@tui.help-circle';
        }
    }

    hasSpaceImage(reservation: Reservation): boolean {
        return !!(reservation.space?.images && reservation.space.images.length > 0);
    }

    getSpaceImageUrl(reservation: Reservation): string {
        return reservation.space?.images?.[0]?.url || '';
    }

    getSpaceName(reservation: Reservation): string {
        // Support pour le mock (spaceName) et l'API réelle (space.name)
        return (reservation as any).spaceName || reservation.space?.name || 'Espace inconnu';
    }

    getSpaceType(reservation: Reservation): string {
        // Support pour le mock (spaceType) et l'API réelle (space.type)
        return (reservation as any).spaceType || reservation.space?.type || '';
    }

    getTranslatedStatus(status: ReservationStatus): string {
        switch (status) {
            case ReservationStatus.Completed:
                return this.translate.instant('reservations.status.COMPLETED');
            case ReservationStatus.Confirmed:
                return this.translate.instant('reservations.status.CONFIRMED');
            case ReservationStatus.Active:
                return this.translate.instant('reservations.status.ACTIVE');
            case ReservationStatus.PendingPayment:
                return this.translate.instant('reservations.status.PENDING_PAYMENT');
            case ReservationStatus.Cancelled:
                return this.translate.instant('reservations.status.CANCELLED');
            default:
                return status;
        }
    }

    // Actions
    onViewReservation(reservation: Reservation): void {
        this.router.navigate(['/admin/reservations', reservation.id]);
    }

    onRegenerateCode(reservation: Reservation): void {
        // TODO: Implement regenerate code
        console.log('Regenerate code for reservation:', reservation.id);
        this.alertService.open('Fonctionnalité non implémentée', { label: 'Régénérer le code' }).subscribe();
    }

    onCancelReservation(reservation: Reservation): void {
        // TODO: Implement cancel reservation
        console.log('Cancel reservation:', reservation.id);
        this.alertService.open('Fonctionnalité non implémentée', { label: 'Annuler la réservation' }).subscribe();
    }

}