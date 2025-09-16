import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiLoader } from '@taiga-ui/core';
import { TuiBadge } from '@taiga-ui/kit';
import { UINewsletterLogEntry } from '../../../../models/UINewsletter';
import { NEWSLETTERS_SERVICE_TOKEN } from '../../admin.providers';
import { NewslettersService } from '../../services/newsletters.service';

@Component({
    standalone: true,
    imports: [
        CommonModule,
        TranslateModule,
        TuiButton,
        TuiIcon,
        TuiBadge,
        TuiLoader
    ],
    selector: 'app-newsletter-logs',
    templateUrl: './newsletter-logs.component.html',
    styleUrls: ['./newsletter-logs.component.scss']
})
export class NewsletterLogsComponent implements OnInit {
    newsletterId: string | null = null;
    logs: UINewsletterLogEntry[] = [];
    isLoading = false;

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
        this.isLoading = true;
        // TODO: Implement getNewsletterLogs method in service
        // this.newslettersService.getNewsletterLogs(this.newsletterId!).subscribe({
        //     next: (logs) => {
        //         this.logs = logs;
        //         this.isLoading = false;
        //     },
        //     error: (error) => {
        //         console.error('Error loading newsletter logs:', error);
        //         this.alerts.open(
        //             this.translate.instant('newsletters.logs.loadError'),
        //             { appearance: 'error' }
        //         ).subscribe();
        //         this.isLoading = false;
        //     }
        // });

        // Mock data for now
        this.logs = [
            {
                id: '1',
                newsletterId: this.newsletterId!,
                userId: 'user1',
                userEmail: 'user1@example.com',
                status: 'SENT',
                sentAt: '2024-01-15T10:05:00Z',
                createdAt: '2024-01-15T10:00:00Z'
            },
            {
                id: '2',
                newsletterId: this.newsletterId!,
                userId: 'user2',
                userEmail: 'user2@example.com',
                status: 'FAILED',
                errorMessage: 'Invalid email address',
                createdAt: '2024-01-15T10:00:00Z'
            },
            {
                id: '3',
                newsletterId: this.newsletterId!,
                userId: 'user3',
                userEmail: 'user3@example.com',
                status: 'PENDING',
                createdAt: '2024-01-15T10:00:00Z'
            }
        ];
        this.isLoading = false;
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
}
