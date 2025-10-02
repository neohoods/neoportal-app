import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, Inject, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiResponsiveDialogService } from '@taiga-ui/addon-mobile';
import { TuiTable } from '@taiga-ui/addon-table';
import {
    TuiAlertService,
    TuiButton,
    TuiHint,
    TuiIcon
} from '@taiga-ui/core';
import {
    TUI_CONFIRM,
    TuiBadge,
    TuiConfirmData
} from '@taiga-ui/kit';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { NewsletterStatus, UINewsletter } from '../../../../models/UINewsletter';
import { NEWSLETTERS_SERVICE_TOKEN } from '../../admin.providers';
import {
    Column,
    TosTableComponent,
} from '../../components/tos-table/tos-table.component';
import { NewslettersService } from '../../services/newsletters.service';

@Component({
    standalone: true,
    imports: [
        TuiButton,
        RouterModule,
        FormsModule,
        TuiTable,
        TuiIcon,
        TuiHint,
        TuiBadge,
        TosTableComponent,
        TranslateModule
    ],
    selector: 'app-newsletters',
    templateUrl: './newsletters.component.html',
    styleUrls: ['./newsletters.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NewslettersComponent {
    @ViewChild('newslettersTable') newslettersTable: any;

    protected newsletters = signal<UINewsletter[]>([]);
    currentNewsletter: UINewsletter | undefined;

    // Available Columns for Display
    columns = computed<Column[]>(() => [
        {
            key: 'subject',
            label: this.translate.instant('newsletters.columns.subject'),
            visible: true,
            sortable: true,
            size: 'l',
        },
        {
            key: 'status',
            label: this.translate.instant('newsletters.columns.status'),
            visible: true,
            custom: true,
            sortable: true,
            size: 'm',
        },
        {
            key: 'audience',
            label: this.translate.instant('newsletters.columns.audience'),
            visible: true,
            custom: true,
            sortable: false,
            size: 'm',
        },
        {
            key: 'scheduledOrSent',
            label: this.translate.instant('newsletters.columns.scheduledOrSent'),
            visible: true,
            custom: true,
            sortable: true,
            size: 'm',
        },
        {
            key: 'createdAt',
            label: this.translate.instant('newsletters.columns.createdAt'),
            visible: true,
            custom: true,
            sortable: true,
            size: 'm',
        }
    ]);

    isMobile: boolean = false;

    // Status enum for template
    NewsletterStatus = NewsletterStatus;

    constructor(
        private breakpointObserver: BreakpointObserver,
        private dialogs: TuiResponsiveDialogService,
        private alerts: TuiAlertService,
        @Inject(NEWSLETTERS_SERVICE_TOKEN) private newslettersService: NewslettersService,
        private translate: TranslateService
    ) {
        this.loadNewsletters();

        this.breakpointObserver
            .observe([Breakpoints.Handset])
            .subscribe((result) => {
                this.isMobile = result.matches;
            });
    }

    private loadNewsletters(): void {
        this.newslettersService.getNewsletters().subscribe((response) => {
            this.newsletters.set(response.newsletters);
        });
    }

    openEditDialog(newsletter: UINewsletter): void {
        // TODO: Implement edit dialog
        console.log('Edit newsletter:', newsletter);
    }

    deleteNewsletter(newsletter: UINewsletter): void {
        if (newsletter.status === NewsletterStatus.SENT) {
            this.alerts.open(
                this.translate.instant('newsletters.messages.cannotDeleteSent'),
                { appearance: 'warning' }
            ).subscribe();
            return;
        }

        const data: TuiConfirmData = {
            content: this.translate.instant('newsletters.messages.confirmDeleteContent'),
            yes: this.translate.instant('newsletters.messages.confirmDeleteYes'),
            no: this.translate.instant('newsletters.messages.confirmDeleteNo'),
        };

        this.dialogs
            .open<boolean>(TUI_CONFIRM, {
                label: this.translate.instant('newsletters.messages.confirmDeleteLabel', { title: newsletter.subject }),
                size: 'm',
                data,
            })
            .subscribe(response => {
                if (response) {
                    this.newslettersService.deleteNewsletter(newsletter.id)
                        .subscribe(() => {
                            // Force refresh of the table
                            if (this.newslettersTable && this.newslettersTable.refresh) {
                                this.newslettersTable.refresh();
                            }
                            this.alerts.open(
                                this.translate.instant('newsletters.messages.deleteSuccess', { title: newsletter.subject }),
                                { appearance: 'positive' }
                            ).subscribe();
                        });
                }
            });
    }

    openScheduleDialog(newsletter: UINewsletter): void {
        // TODO: Implement schedule dialog
        console.log('Schedule newsletter:', newsletter);
    }

    sendNewsletter(newsletter: UINewsletter): void {
        if (newsletter.status !== NewsletterStatus.DRAFT && newsletter.status !== NewsletterStatus.SCHEDULED) {
            this.alerts.open(
                this.translate.instant('newsletters.restrictions.cannotSendNewsletter'),
                { appearance: 'warning' }
            ).subscribe();
            return;
        }

        const data: TuiConfirmData = {
            content: this.translate.instant('newsletters.messages.confirmSendContent'),
            yes: this.translate.instant('newsletters.messages.confirmSendYes'),
            no: this.translate.instant('newsletters.messages.confirmSendNo'),
        };

        this.dialogs
            .open<boolean>(TUI_CONFIRM, {
                label: this.translate.instant('newsletters.messages.confirmSendLabel', { title: newsletter.subject }),
                size: 'm',
                data,
            })
            .subscribe(response => {
                if (response) {
                    this.newslettersService.sendNewsletter(newsletter.id)
                        .subscribe(() => {
                            this.loadNewsletters();
                            this.alerts.open(
                                this.translate.instant('newsletters.messages.sendSuccess', { title: newsletter.subject }),
                                { appearance: 'positive' }
                            ).subscribe();
                        });
                }
            });
    }

    getDataFunction = (
        searchText?: string,
        sortBy?: string,
        sortOrder?: 'asc' | 'desc',
        page?: number,
        pageSize?: number
    ): Observable<any> => {
        return this.newslettersService.getNewsletters(page, pageSize).pipe(
            map((response) => {
                // Filter newsletters based on search text
                let filteredNewsletters = response.newsletters;
                if (searchText && searchText.trim()) {
                    const searchLower = searchText.toLowerCase().trim();
                    filteredNewsletters = response.newsletters.filter((newsletter: UINewsletter) =>
                        newsletter.subject?.toLowerCase().includes(searchLower) ||
                        newsletter.status?.toLowerCase().includes(searchLower) ||
                        this.getAudienceDisplayText(newsletter.audience)?.toLowerCase().includes(searchLower)
                    );
                }

                // Sort newsletters if sortBy is specified
                if (sortBy) {
                    filteredNewsletters = [...filteredNewsletters].sort((a, b) => {
                        let aValue: any;
                        let bValue: any;

                        if (sortBy === 'scheduledOrSent') {
                            aValue = a.sentAt || a.scheduledAt;
                            bValue = b.sentAt || b.scheduledAt;
                        } else if (sortBy === 'audience') {
                            aValue = this.getAudienceDisplayText(a.audience);
                            bValue = this.getAudienceDisplayText(b.audience);
                        } else {
                            aValue = a[sortBy as keyof UINewsletter];
                            bValue = b[sortBy as keyof UINewsletter];
                        }

                        // Handle undefined values
                        if (aValue === undefined && bValue === undefined) return 0;
                        if (aValue === undefined) return 1;
                        if (bValue === undefined) return -1;

                        if (aValue === bValue) return 0;

                        const comparison = aValue < bValue ? -1 : 1;
                        return sortOrder === 'desc' ? -comparison : comparison;
                    });
                }

                // Calculate pagination
                const totalItems = filteredNewsletters.length;
                const itemsPerPage = pageSize || 12;
                const totalPages = Math.ceil(totalItems / itemsPerPage);
                const currentPage = page || 1;
                const startIndex = (currentPage - 1) * itemsPerPage;
                const endIndex = startIndex + itemsPerPage;
                const paginatedNewsletters = filteredNewsletters.slice(startIndex, endIndex);

                // Mettre à jour le Signal avec les nouvelles données
                this.newsletters.set(filteredNewsletters);
                return {
                    totalPages,
                    totalItems,
                    currentPage,
                    itemsPerPage,
                    items: paginatedNewsletters
                };
            })
        );
    }

    getStatusBadgeAppearance(status: NewsletterStatus): string {
        switch (status) {
            case NewsletterStatus.DRAFT:
                return 'warning'; // Jaune/Orange pour brouillon
            case NewsletterStatus.SCHEDULED:
                return 'info'; // Bleu pour programmée
            case NewsletterStatus.SENDING:
                return 'primary'; // Bleu foncé pour en cours d'envoi
            case NewsletterStatus.SENT:
                return 'success'; // Vert pour envoyée
            case NewsletterStatus.CANCELLED:
                return 'neutral'; // Gris pour annulée
            case NewsletterStatus.FAILED:
                return 'error'; // Rouge pour échec
            default:
                return 'neutral';
        }
    }

    getDataProperty(item: any, key: string): any {
        return item[key];
    }

    getAudienceDisplayText(audience: any): string {
        if (!audience) {
            return '-';
        }

        switch (audience.type) {
            case 'ALL':
                return this.translate.instant('newsletters.audience.all');
            case 'USER_TYPES':
                if (audience.userTypes && audience.userTypes.length > 0) {
                    return audience.userTypes.map((type: string) =>
                        this.translate.instant(`newsletters.userTypes.${type.toLowerCase()}`)
                    ).join(', ');
                }
                return this.translate.instant('newsletters.audience.userTypes');
            case 'SPECIFIC_USERS':
                if (audience.userIds && audience.userIds.length > 0) {
                    return `${audience.userIds.length} ${this.translate.instant('newsletters.audience.users')}`;
                }
                return this.translate.instant('newsletters.audience.specificUsers');
            default:
                return audience.type || '-';
        }
    }

    testNewsletter(newsletter: UINewsletter): void {
        this.newslettersService.testNewsletter(newsletter.id).subscribe({
            next: () => {
                this.alerts.open(
                    this.translate.instant('newsletters.messages.testSuccess'),
                    { appearance: 'positive' }
                ).subscribe();
            },
            error: (error) => {
                console.error('Error testing newsletter:', error);
                this.alerts.open(
                    this.translate.instant('newsletters.messages.testError'),
                    { appearance: 'error' }
                ).subscribe();
            }
        });
    }

    formatDate(dateString: string | undefined): string {
        if (!dateString) {
            return '-';
        }
        const date = new Date(dateString);
        if (isNaN(date.getTime())) {
            return '-';
        }
        return date.toLocaleDateString('fr-FR', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    getScheduledOrSentDate(newsletter: UINewsletter): string {
        if (newsletter.sentAt) {
            return this.formatDate(newsletter.sentAt);
        } else if (newsletter.scheduledAt) {
            return this.formatDate(newsletter.scheduledAt);
        }
        return '-';
    }

    getScheduledOrSentLabel(newsletter: UINewsletter): string {
        if (newsletter.sentAt) {
            return this.translate.instant('newsletters.columns.sentAt');
        } else if (newsletter.scheduledAt) {
            return this.translate.instant('newsletters.columns.scheduledAt');
        }
        return '-';
    }


}
