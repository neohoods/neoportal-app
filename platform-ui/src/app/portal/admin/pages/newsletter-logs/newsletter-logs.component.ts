import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiIcon, TuiLoader } from '@taiga-ui/core';
import { TuiBadge } from '@taiga-ui/kit';
import { UINewsletterLogEntry } from '../../../../models/UINewsletter';
import { NEWSLETTERS_SERVICE_TOKEN } from '../../admin.providers';
import { NewslettersService } from '../../services/newsletters.service';

@Component({
    standalone: true,
    imports: [
        CommonModule,
        TranslateModule,
        TuiIcon,
        TuiBadge,
        TuiLoader
    ],
    selector: 'app-newsletter-logs',
    templateUrl: './newsletter-logs.component.html',
    styleUrls: ['./newsletter-logs.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class NewsletterLogsComponent implements OnInit {
    newsletterId: string | null = null;

    // Convert to signals for reactive updates
    logs = signal<UINewsletterLogEntry[]>([]);
    isLoading = signal(false);
    currentPage = signal(1);
    pageSize = signal(10);
    totalItems = signal(0);
    totalPages = signal(0);

    constructor(
        private route: ActivatedRoute,
        @Inject(NEWSLETTERS_SERVICE_TOKEN) private newslettersService: NewslettersService,
        private alerts: TuiAlertService,
        private translate: TranslateService
    ) { }

    ngOnInit(): void {
        this.newsletterId = this.route.snapshot.paramMap.get('newsletterId');
        if (this.newsletterId) {
            this.loadLogs();
        }
    }

    private loadLogs(): void {
        this.isLoading.set(true);
        this.newslettersService.getNewsletterLogs(this.newsletterId!, this.currentPage(), this.pageSize()).subscribe({
            next: (logs) => {
                this.logs.set(logs);
                this.isLoading.set(false);
                // Note: The current API doesn't return pagination info, so we'll use the logs length
                this.totalItems.set(logs.length);
                this.totalPages.set(Math.ceil(logs.length / this.pageSize()));
            },
            error: (error) => {
                console.error('Error loading newsletter logs:', error);
                this.alerts.open(
                    this.translate.instant('newsletters.logs.loadError'),
                    { appearance: 'error' }
                ).subscribe();
                this.isLoading.set(false);
            }
        });
    }

    onPageChange(page: number): void {
        this.currentPage.set(page);
        this.loadLogs();
    }

    // Public method to refresh logs from external components
    refreshLogs(): void {
        this.loadLogs();
    }

    getStatusBadgeAppearance(status: string): string {
        switch (status) {
            case 'SENT':
                return 'status_success';
            case 'FAILED':
                return 'status_error';
            case 'BOUNCED':
                return 'status_warning';
            case 'PENDING':
                return 'status_info';
            default:
                return 'status_neutral';
        }
    }

    getStatusLabel(status: string): string {
        return this.translate.instant(`newsletters.logs.status.${status.toLowerCase()}`);
    }

    getMin(a: number, b: number): number {
        return Math.min(a, b);
    }
}
