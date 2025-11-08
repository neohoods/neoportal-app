import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiDialogService, TuiIcon, TuiLoader, TuiNotification } from '@taiga-ui/core';
import { TuiChip, TuiPagination, TuiTabs } from '@taiga-ui/kit';

import { inject } from '@angular/core';
import { AUTH_SERVICE_TOKEN } from '../../../../global.provider';
import { UserInfo } from '../../../../services/auth.service';
import { UnitsHubApiService } from '../../../../api-client/api/unitsHubApi.service';
import { Reservation } from '../../services/reservations.service';
import { Space } from '../../services/spaces.service';
import { RESERVATIONS_SERVICE_TOKEN, SPACES_SERVICE_TOKEN } from '../../spaces.provider';

@Component({
    selector: 'app-my-reservations',
    standalone: true,
    imports: [
        CommonModule,
        TranslateModule,
        TuiButton,
        TuiIcon,
        TuiLoader,
        TuiTabs,
        TuiChip,
        TuiPagination,
        TuiNotification
    ],
    templateUrl: './my-reservations.component.html',
    styleUrls: ['./my-reservations.component.scss']
})
export class MyReservationsComponent implements OnInit {
    private reservationsService = inject(RESERVATIONS_SERVICE_TOKEN);
    private spacesService = inject(SPACES_SERVICE_TOKEN);
    private authService = inject(AUTH_SERVICE_TOKEN);
    private unitsApiService = inject(UnitsHubApiService);
    private translate = inject(TranslateService);
    private router = inject(Router);
    private dialogs = inject(TuiDialogService);
    private alerts = inject(TuiAlertService);

    reservations = signal<Reservation[]>([]);
    spaces = signal<Map<string, Space>>(new Map());
    loading = signal(false);
    activeTab = 0; // Past (0), Active (1), Future (2)

    // User can reserve flag
    canReserve = signal<boolean | null>(null);
    loadingCanReserve = signal(false);
    currentUser!: UserInfo;

    // Pagination signals - separate for each tab
    currentPagePast = signal(1);
    currentPageActive = signal(1);
    currentPagePending = signal(1);
    currentPageFuture = signal(1);
    itemsPerPage = signal(9);

    ngOnInit(): void {
        this.currentUser = this.authService.getCurrentUserInfo();
        this.loadReservations();
        // Check if user can make reservations
        this.checkCanReserve();
    }

    // Get count for each status
    getActiveCount(): number {
        return this.getFilteredReservations('ACTIVE').length;
    }

    getPastCount(): number {
        return this.getFilteredReservations('PAST').length;
    }

    getFutureCount(): number {
        return this.getFilteredReservations('FUTURE').length;
    }

    getPendingPaymentCount(): number {
        return this.getTotalItemsForStatus('PENDING_PAYMENT');
    }

    // Determine default tab based on available reservations
    // New order: Past (0), Active (1), Future (2)
    getDefaultTab(): number {
        const activeCount = this.getActiveCount();
        const futureCount = this.getFutureCount();
        const pastCount = this.getPastCount();

        // Priority: Active > Future > Past
        if (activeCount > 0) return 1; // Active tab (index 1)
        if (futureCount > 0) return 2; // Future tab (index 2)
        if (pastCount > 0) return 0; // Past tab (index 0)

        return 0; // Default to past tab
    }

    private loadReservations(): void {
        this.loading.set(true);

        this.reservationsService.getMyReservations().subscribe({
            next: (reservations) => {
                this.reservations.set(reservations);
                this.loadSpacesForReservations(reservations);
                // Set default tab after loading reservations
                this.activeTab = this.getDefaultTab();
                this.loading.set(false);
            },
            error: (error) => {
                console.error('Error loading reservations:', error);
                this.alerts.open(
                    this.translate.instant('reservations.loadError'),
                    { appearance: 'negative' }
                ).subscribe();
                this.loading.set(false);
            }
        });
    }


    getStatusAppearance(status: string): string {
        switch (status) {
            case 'PENDING_PAYMENT':
                return 'warning'; // Orange - en attente de paiement
            case 'PAYMENT_FAILED':
                return 'negative'; // Rouge - échec de paiement
            case 'EXPIRED':
                return 'negative'; // Rouge - expiré
            case 'CONFIRMED':
                return 'positive'; // Vert - confirmé
            case 'ACTIVE':
                return 'accent'; // Bleu - actif
            case 'COMPLETED':
                return 'positive'; // Vert - terminé
            case 'CANCELLED':
                return 'negative'; // Rouge - annulé
            case 'REFUNDED':
                return 'info'; // Bleu clair - remboursé
            default:
                return 'outline'; // Gris - statut inconnu
        }
    }

    private loadSpacesForReservations(reservations: Reservation[]): void {
        const spaceIds = [...new Set(reservations.map(r => r.spaceId))];

        // Use cache service to load all spaces in one request
        this.spacesService.loadSpacesByIds(spaceIds).subscribe({
            next: (spacesMap) => {
                this.spaces.set(spacesMap);
            },
            error: (error) => {
                console.error('Error loading spaces:', error);
                this.alerts.open(
                    this.translate.instant('spaces.loadError'),
                    { appearance: 'negative' }
                ).subscribe();
            }
        });
    }

    getStatusLabel(status: string): string {
        return this.translate.instant(`reservations.status.${status}`);
    }

    getStatusClass(status: string): string {
        return `status-${status.toLowerCase()}`;
    }

    formatDate(dateString: string): string {
        return new Date(dateString).toLocaleDateString('fr-FR');
    }

    onBackToSpaces(): void {
        this.router.navigate(['/spaces/list']);
    }

    onViewDetails(reservation: Reservation): void {
        console.log('Navigating to reservation detail:', reservation.id);
        this.router.navigate(['/spaces/reservations', reservation.id]).then(
            success => {
                console.log('Navigation success:', success);
                // Scroll to top after navigation
                window.scrollTo({ top: 0, behavior: 'smooth' });
            },
            error => console.error('Navigation error:', error)
        );
    }

    getSpaceForReservation(reservation: Reservation): Space | undefined {
        return this.spaces().get(reservation.spaceId);
    }

    getFilteredReservations(status: string): Reservation[] {
        let filtered: Reservation[];
        const today = new Date();
        today.setHours(0, 0, 0, 0);

        if (status === 'PAST') {
            // Past tab includes COMPLETED or confirmed/future that have ended
            filtered = this.reservations().filter(r => {
                const endDate = new Date(r.endDate);
                endDate.setHours(0, 0, 0, 0);
                return r.status === 'COMPLETED' || endDate < today;
            });
        } else if (status === 'ACTIVE') {
            // Active tab includes only ACTIVE status
            filtered = this.reservations().filter(r => r.status === 'ACTIVE');
        } else if (status === 'FUTURE') {
            // Future tab includes CONFIRMED that haven't started yet
            filtered = this.reservations().filter(r => {
                const startDate = new Date(r.startDate);
                startDate.setHours(0, 0, 0, 0);
                return r.status === 'CONFIRMED' && startDate >= today;
            });
        } else if (status === 'PENDING_PAYMENT') {
            // Pending Payment tab includes only PENDING_PAYMENT
            filtered = this.reservations().filter(r => r.status === 'PENDING_PAYMENT');
        } else {
            filtered = this.reservations().filter(r => r.status === status);
        }

        // Apply pagination based on the current tab
        let currentPage: number;
        if (status === 'COMPLETED') {
            currentPage = this.currentPagePast();
        } else if (status === 'ACTIVE') {
            currentPage = this.currentPageActive();
        } else if (status === 'PENDING_PAYMENT') {
            currentPage = this.currentPagePending();
        } else if (status === 'FUTURE') {
            currentPage = this.currentPageFuture();
        } else {
            currentPage = 1;
        }

        const startIndex = (currentPage - 1) * this.itemsPerPage();
        const endIndex = startIndex + this.itemsPerPage();
        return filtered.slice(startIndex, endIndex);
    }

    getTotalItemsForStatus(status: string): number {
        return this.reservations().filter(r => r.status === status).length;
    }

    getTotalPagesForStatus(status: string): number {
        let totalItems: number;

        if (status === 'PAST') {
            totalItems = this.getPastCount();
        } else if (status === 'ACTIVE') {
            totalItems = this.getActiveCount();
        } else if (status === 'FUTURE') {
            totalItems = this.getFutureCount();
        } else if (status === 'PENDING_PAYMENT') {
            totalItems = this.getPendingPaymentCount();
        } else {
            totalItems = this.getTotalItemsForStatus(status);
        }

        return Math.ceil(totalItems / this.itemsPerPage());
    }

    onTabChange(index: number): void {
        this.activeTab = index;
        // Reset pagination for the new tab
        this.currentPagePast.set(1);
        this.currentPageActive.set(1);
        this.currentPagePending.set(1);
        this.currentPageFuture.set(1);
    }

    getPendingPaymentReservations(): Reservation[] {
        return this.reservations().filter(r => r.status === 'PENDING_PAYMENT');
    }

    getTranslatedSpaceType(spaceType?: string): string {
        if (!spaceType) return '';
        return this.translate.instant(`spaces.spaceType.${spaceType.toLowerCase()}`);
    }

    getSpaceTypeIcon(spaceType?: string): string {
        switch (spaceType?.toUpperCase()) {
            case 'GUEST_ROOM':
                return '@tui.home';
            case 'COMMON_ROOM':
                return '@tui.users';
            case 'COWORKING':
                return '@tui.monitor';
            case 'PARKING':
                return '@tui.car';
            default:
                return '@tui.home';
        }
    }

    trackByReservationId(index: number, reservation: Reservation): string {
        return reservation.id;
    }

    onPageChange(page: number, tabType: 'past' | 'active' | 'pending' | 'future'): void {
        if (tabType === 'past') {
            this.currentPagePast.set(page);
        } else if (tabType === 'active') {
            this.currentPageActive.set(page);
        } else if (tabType === 'pending') {
            this.currentPagePending.set(page);
        } else if (tabType === 'future') {
            this.currentPageFuture.set(page);
        }
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
