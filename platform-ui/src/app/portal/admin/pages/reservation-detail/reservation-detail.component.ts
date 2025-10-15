import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiDialogService, TuiIcon, TuiLoader } from '@taiga-ui/core';
import { TuiTabs } from '@taiga-ui/kit';

import { inject } from '@angular/core';
import { UIReservation } from '../../../../models/UIReservation';
import { UIUser } from '../../../../models/UIUser';
import { ADMIN_RESERVATIONS_SERVICE_TOKEN, USERS_SERVICE_TOKEN } from '../../admin.providers';

interface AuditLog {
    id: string;
    reservationId: string;
    eventType: string;
    oldValue?: string;
    newValue?: string;
    logMessage: string;
    performedBy: string;
    createdAt: string;
}

@Component({
    selector: 'app-reservation-detail',
    standalone: true,
    imports: [
        CommonModule,
        TuiTabs,
        TuiButton,
        TuiIcon,
        TuiLoader,
        TranslateModule,
        RouterLink
    ],
    templateUrl: './reservation-detail.component.html',
    styleUrls: ['./reservation-detail.component.scss']
})
export class ReservationDetailComponent implements OnInit {
    private reservationsService = inject(ADMIN_RESERVATIONS_SERVICE_TOKEN);
    private usersService = inject(USERS_SERVICE_TOKEN);
    private translate = inject(TranslateService);
    private alertService = inject(TuiAlertService);
    private dialogService = inject(TuiDialogService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);

    reservation = signal<UIReservation | null>(null);
    user = signal<UIUser | null>(null);
    loading = signal(false);
    activeTab = 0;
    auditLogs = signal<AuditLog[]>([]);
    auditLogsLoading = signal(false);

    ngOnInit(): void {
        this.loadReservation();
    }

    private loadReservation(): void {
        this.loading.set(true);
        const reservationId = this.route.snapshot.paramMap.get('id');

        if (!reservationId) {
            this.router.navigate(['/admin/reservations']);
            return;
        }

        this.reservationsService.getReservation(reservationId).subscribe({
            next: (reservation: UIReservation) => {
                this.reservation.set(reservation);
                this.loadUser(reservation.userId);
                this.loadAuditLogs(); // Load audit logs when reservation is loaded
                this.loading.set(false);
            },
            error: (error: any) => {
                console.error('Error loading reservation:', error);
                this.alertService.open(
                    this.translate.instant('reservations.admin.loadError'),
                    { appearance: 'negative' }
                ).subscribe();
                this.router.navigate(['/admin/reservations']);
                this.loading.set(false);
            }
        });
    }

    private loadUser(userId: string): void {
        this.usersService.getUser(userId).subscribe({
            next: (user) => {
                this.user.set(user);
            },
            error: (error) => {
                console.error('Error loading user:', error);
                this.alertService.open(
                    this.translate.instant('users.loadError'),
                    { appearance: 'negative' }
                ).subscribe();
            }
        });
    }

    getStatusLabel(status: string): string {
        return this.translate.instant(`reservations.status.${status}`) || status;
    }

    getStatusClass(status: string): string {
        return `status-${status.toLowerCase()}`;
    }

    formatDate(dateString: string): string {
        return new Date(dateString).toLocaleDateString('fr-FR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    onRegenerateAccessCode(): void {
        const reservation = this.reservation();
        if (!reservation) return;

        const message = this.translate.instant('reservations.admin.confirmRegenerateCode');

        this.dialogService.open(message, {
            label: this.translate.instant('reservations.admin.regenerateCode'),
            size: 's',
            data: { button: 'Yes' }
        }).subscribe({
            next: () => {
                // TODO: Implement regenerate access code API call
                this.alertService.open(
                    this.translate.instant('reservations.admin.codeRegenerated')
                ).subscribe();
            }
        });
    }

    onCancelReservation(): void {
        const reservation = this.reservation();
        if (!reservation) return;

        const message = this.translate.instant('reservations.admin.confirmCancel');

        this.dialogService.open(message, {
            label: this.translate.instant('reservations.admin.cancel'),
            size: 's',
            data: { button: 'Yes' }
        }).subscribe({
            next: () => {
                this.reservationsService.cancelReservation(reservation.id).subscribe({
                    next: () => {
                        this.alertService.open(
                            this.translate.instant('reservations.admin.reservationCancelled')
                        ).subscribe();
                        this.router.navigate(['/admin/reservations']);
                    },
                    error: (error: any) => {
                        console.error('Error cancelling reservation:', error);
                        this.alertService.open(
                            this.translate.instant('reservations.admin.cancelError'),
                            { appearance: 'negative' }
                        ).subscribe();
                    }
                });
            }
        });
    }

    onBack(): void {
        this.router.navigate(['/admin/reservations']);
    }

    onViewUser(): void {
        const user = this.user();
        if (user) {
            this.router.navigate(['/admin/users', user.id, 'edit']);
        }
    }

    onTabChange(index: number): void {
        this.activeTab = index;
    }

    getDurationDays(startDate: string, endDate: string): number {
        const start = new Date(startDate);
        const end = new Date(endDate);
        const diffTime = Math.abs(end.getTime() - start.getTime());
        return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    }

    getPaymentStatus(status: string): string {
        switch (status) {
            case 'PENDING_PAYMENT':
                return this.translate.instant('reservations.admin.pendingPayment');
            case 'CONFIRMED':
                return this.translate.instant('reservations.admin.paid');
            case 'CANCELLED':
                return this.translate.instant('reservations.admin.cancelled');
            case 'COMPLETED':
                return this.translate.instant('reservations.admin.completed');
            default:
                return status;
        }
    }

    isAccessCodeValid(): boolean {
        const reservation = this.reservation();
        if (!reservation?.accessCode) return false;

        const now = new Date();
        const expiresAt = new Date(reservation.accessCode.expiresAt);
        return now < expiresAt;
    }

    copyAccessCode(): void {
        const reservation = this.reservation();
        if (reservation?.accessCode) {
            navigator.clipboard.writeText(reservation.accessCode.code).then(() => {
                this.alertService.open(
                    this.translate.instant('reservations.admin.codeCopied')
                ).subscribe();
            });
        }
    }

    getUserDisplayName(user: UIUser): string {
        return `@${user.username}`;
    }

    // Audit Log Methods
    loadAuditLogs(): void {
        const reservation = this.reservation();
        if (!reservation) return;

        this.auditLogsLoading.set(true);

        // TODO: Replace with actual API call when backend is ready
        // For now, create mock data
        setTimeout(() => {
            const mockLogs: AuditLog[] = [
                {
                    id: '1',
                    reservationId: reservation.id,
                    eventType: 'STATUS_CHANGE',
                    oldValue: 'PENDING_PAYMENT',
                    newValue: 'CONFIRMED',
                    logMessage: 'Status changed from PENDING_PAYMENT to CONFIRMED',
                    performedBy: 'system',
                    createdAt: new Date().toISOString()
                },
                {
                    id: '2',
                    reservationId: reservation.id,
                    eventType: 'CODE_GENERATED',
                    oldValue: undefined,
                    newValue: 'ABC123',
                    logMessage: 'Access code generated: ABC123',
                    performedBy: 'system',
                    createdAt: new Date(Date.now() - 3600000).toISOString()
                }
            ];

            this.auditLogs.set(mockLogs);
            this.auditLogsLoading.set(false);
        }, 1000);
    }

    refreshAuditLogs(): void {
        this.loadAuditLogs();
    }

    trackByLogId(index: number, log: AuditLog): string {
        return log.id;
    }

    getEventTypeIcon(eventType: string): string {
        switch (eventType) {
            case 'STATUS_CHANGE':
                return '@tui.refresh';
            case 'CODE_GENERATED':
            case 'CODE_REGENERATED':
                return '@tui.key';
            case 'PAYMENT_RECEIVED':
                return '@tui.credit-card';
            case 'CANCELLED':
                return '@tui.x';
            case 'CONFIRMED':
                return '@tui.check';
            case 'ENTRY_LOGGED':
                return '@tui.log-in';
            case 'EXIT_LOGGED':
                return '@tui.log-out';
            default:
                return '@tui.info';
        }
    }

    getEventTypeClass(eventType: string): string {
        switch (eventType) {
            case 'STATUS_CHANGE':
                return 'status-change';
            case 'CODE_GENERATED':
            case 'CODE_REGENERATED':
                return 'code-event';
            case 'PAYMENT_RECEIVED':
                return 'payment-event';
            case 'CANCELLED':
                return 'cancelled-event';
            case 'CONFIRMED':
                return 'confirmed-event';
            case 'ENTRY_LOGGED':
            case 'EXIT_LOGGED':
                return 'access-event';
            default:
                return 'default-event';
        }
    }

    getEventTypeLabel(eventType: string): string {
        const translationKey = `reservations.admin.auditLog.eventType.${eventType}`;
        const translation = this.translate.instant(translationKey);
        // If translation is the same as the key, return a fallback
        return translation !== translationKey ? translation : eventType.replace(/_/g, ' ');
    }

    formatDateTime(dateString: string): string {
        return new Date(dateString).toLocaleString('fr-FR', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }
}
