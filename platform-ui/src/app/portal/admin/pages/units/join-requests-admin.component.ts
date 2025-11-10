import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon } from '@taiga-ui/core';
import { UnitsAdminApiService } from '../../../../api-client/api/unitsAdminApi.service';
import { UNITS_SERVICE_TOKEN } from '../../admin.providers';
import { UnitsService } from '../../services/units.service';

@Component({
    standalone: true,
    imports: [
        TuiButton,
        TuiIcon,
        RouterModule,
        TranslateModule,
        DatePipe
    ],
    selector: 'app-join-requests-admin',
    templateUrl: './join-requests-admin.component.html',
    styleUrls: ['./join-requests-admin.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JoinRequestsAdminComponent implements OnInit {
    protected joinRequests: any[] = [];
    protected loadingJoinRequests = false;

    constructor(
        @Inject(UNITS_SERVICE_TOKEN) private unitsService: UnitsService,
        private unitsAdminApiService: UnitsAdminApiService,
        private translate: TranslateService,
        private alerts: TuiAlertService,
        private cdr: ChangeDetectorRef
    ) {}

    ngOnInit(): void {
        this.loadJoinRequests();
    }

    loadJoinRequests(): void {
        this.loadingJoinRequests = true;
        this.cdr.markForCheck();
        
        this.unitsAdminApiService.getAllJoinRequests().subscribe({
            next: (response: any) => {
                // Handle Flux or array response
                if (Array.isArray(response)) {
                    this.joinRequests = response;
                    this.loadingJoinRequests = false;
                    this.cdr.markForCheck();
                } else if (response && typeof response.subscribe === 'function') {
                    const requests: any[] = [];
                    response.subscribe({
                        next: (request: any) => requests.push(request),
                        complete: () => {
                            this.joinRequests = requests;
                            this.loadingJoinRequests = false;
                            this.cdr.markForCheck();
                        },
                        error: (err: any) => {
                            console.error('Failed to load join requests:', err);
                            this.loadingJoinRequests = false;
                            this.cdr.markForCheck();
                        }
                    });
                } else {
                    this.joinRequests = [];
                    this.loadingJoinRequests = false;
                    this.cdr.markForCheck();
                }
            },
            error: (error: any) => {
                console.error('Failed to load join requests:', error);
                this.loadingJoinRequests = false;
                this.cdr.markForCheck();
            }
        });
    }

    approveJoinRequest(requestId: string): void {
        this.unitsAdminApiService.approveJoinRequestAdmin(requestId).subscribe({
            next: () => {
                this.alerts.open(
                    this.translate.instant('settings.units.joinRequests.requestApproved'),
                    { appearance: 'positive' }
                ).subscribe();
                this.loadJoinRequests();
            },
            error: (error: any) => {
                console.error('Failed to approve join request:', error);
                this.alerts.open(
                    this.translate.instant('settings.units.joinRequests.requestApproved') + ': ' + (error.message || 'Erreur'),
                    { appearance: 'negative' }
                ).subscribe();
            }
        });
    }

    rejectJoinRequest(requestId: string): void {
        this.unitsAdminApiService.rejectJoinRequestAdmin(requestId).subscribe({
            next: () => {
                this.alerts.open(
                    this.translate.instant('settings.units.joinRequests.requestRejected'),
                    { appearance: 'positive' }
                ).subscribe();
                this.loadJoinRequests();
            },
            error: (error: any) => {
                console.error('Failed to reject join request:', error);
                this.alerts.open(
                    this.translate.instant('settings.units.joinRequests.requestRejected') + ': ' + (error.message || 'Erreur'),
                    { appearance: 'negative' }
                ).subscribe();
            }
        });
    }
}

