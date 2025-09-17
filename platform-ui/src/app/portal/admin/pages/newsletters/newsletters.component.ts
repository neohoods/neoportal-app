import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
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
import { map } from 'rxjs';
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
    protected newsletters: UINewsletter[] = [];
    currentNewsletter: UINewsletter | undefined;

    // Available Columns for Display
    columns: Column[] = [];

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
        this.columns = [
            {
                key: 'subject',
                label: this.translate.instant('newsletters.columns.title'),
                visible: true,
                sortable: true,
                size: 'l',
            },
            {
                key: 'audience',
                label: this.translate.instant('newsletters.columns.audience'),
                custom: true,
                visible: true,
                sortable: true,
                size: 'm',
            },
            {
                key: 'status',
                label: this.translate.instant('newsletters.columns.status'),
                custom: true,
                visible: true,
                sortable: true,
                size: 'm',
            },
            {
                key: 'createdAt',
                label: this.translate.instant('newsletters.columns.createdAt'),
                visible: true,
                sortable: true,
                size: 'm',
            },
            {
                key: 'scheduledOrSent',
                label: this.translate.instant('newsletters.columns.scheduledOrSent'),
                custom: true,
                visible: true,
                sortable: true,
                size: 'm',
            },
            {
                key: 'recipientCount',
                label: this.translate.instant('newsletters.columns.recipientCount'),
                visible: true,
                sortable: false,
                size: 's',
            }
        ];

        this.loadNewsletters();

        this.breakpointObserver
            .observe([Breakpoints.Handset])
            .subscribe((result) => {
                this.isMobile = result.matches;
            });
    }

    private loadNewsletters(): void {
        this.newslettersService.getNewsletters().subscribe((response) => {
            this.newsletters = response.newsletters;
        });
    }

    openEditDialog(newsletter: UINewsletter): void {
        // TODO: Implement edit dialog
        console.log('Edit newsletter:', newsletter);
    }

    deleteNewsletter(newsletter: UINewsletter): void {
        if (newsletter.status !== NewsletterStatus.DRAFT) {
            this.alerts.open(
                this.translate.instant('newsletters.cannotDeleteNonDraft'),
                { appearance: 'warning' }
            ).subscribe();
            return;
        }

        const data: TuiConfirmData = {
            content: this.translate.instant('newsletters.confirmDeleteContent'),
            yes: this.translate.instant('newsletters.confirmDeleteYes'),
            no: this.translate.instant('newsletters.confirmDeleteNo'),
        };

        this.dialogs
            .open<boolean>(TUI_CONFIRM, {
                label: this.translate.instant('newsletters.confirmDeleteLabel', { title: newsletter.subject }),
                size: 'm',
                data,
            })
            .subscribe(response => {
                if (response) {
                    this.newslettersService.deleteNewsletter(newsletter.id)
                        .subscribe(() => {
                            this.loadNewsletters();
                            this.alerts.open(
                                this.translate.instant('newsletters.deleteSuccess', { title: newsletter.subject }),
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
                this.translate.instant('newsletters.cannotSendNewsletter'),
                { appearance: 'warning' }
            ).subscribe();
            return;
        }

        const data: TuiConfirmData = {
            content: this.translate.instant('newsletters.confirmSendContent'),
            yes: this.translate.instant('newsletters.confirmSendYes'),
            no: this.translate.instant('newsletters.confirmSendNo'),
        };

        this.dialogs
            .open<boolean>(TUI_CONFIRM, {
                label: this.translate.instant('newsletters.confirmSendLabel', { title: newsletter.subject }),
                size: 'm',
                data,
            })
            .subscribe(response => {
                if (response) {
                    this.newslettersService.sendNewsletter(newsletter.id)
                        .subscribe(() => {
                            this.loadNewsletters();
                            this.alerts.open(
                                this.translate.instant('newsletters.sendSuccess', { title: newsletter.subject }),
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
    ) => {
        return this.newslettersService.getNewsletters(page, pageSize).pipe(
            map((response) => {
                return {
                    totalPages: response.totalPages,
                    totalItems: response.totalItems,
                    currentPage: response.currentPage,
                    itemsPerPage: response.itemsPerPage,
                    items: response.newsletters
                }
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
            return this.translate.instant('newsletters.audience.all');
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
                    return `${audience.userIds.length} ${this.translate.instant('newsletters.audience.specificUsers')}`;
                }
                return this.translate.instant('newsletters.audience.specificUsers');
            default:
                return this.translate.instant('newsletters.audience.all');
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
