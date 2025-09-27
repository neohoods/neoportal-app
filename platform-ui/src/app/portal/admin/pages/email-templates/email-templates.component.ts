import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiResponsiveDialogService } from '@taiga-ui/addon-mobile';
import { TuiTable } from '@taiga-ui/addon-table';
import {
    TuiAlertService,
    TuiButton,
    TuiDialogService,
    TuiHint,
    TuiIcon
} from '@taiga-ui/core';
import {
    TUI_CONFIRM,
    TuiBadge,
    TuiConfirmData
} from '@taiga-ui/kit';
import { map } from 'rxjs';
import { EmailTemplateType, UIEmailTemplate } from '../../../../models/UIEmailTemplate';
import { EMAIL_TEMPLATES_SERVICE_TOKEN } from '../../admin.providers';
import {
    Column,
    TosTableComponent,
} from '../../components/tos-table/tos-table.component';
import { EmailTemplatesService } from '../../services/email-templates.service';

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
    selector: 'app-email-templates',
    templateUrl: './email-templates.component.html',
    styleUrls: ['./email-templates.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailTemplatesComponent {
    protected emailTemplates: UIEmailTemplate[] = [];
    currentEmailTemplate: UIEmailTemplate | undefined;

    // Available Columns for Display
    protected columns: Column[] = [];


    // Function to get data for the table
    protected getDataFunction = (
        searchText?: string,
        sortBy?: string,
        sortOrder?: 'asc' | 'desc',
        page?: number,
        pageSize?: number
    ) => {
        return this.emailTemplatesService.getEmailTemplates().pipe(
            map((templates) => {
                return {
                    totalPages: 1,
                    totalItems: templates.length,
                    currentPage: 1,
                    itemsPerPage: templates.length,
                    items: templates
                }
            })
        );
    };

    constructor(
        @Inject(EMAIL_TEMPLATES_SERVICE_TOKEN) private emailTemplatesService: EmailTemplatesService,
        @Inject(TuiAlertService) private readonly alerts: TuiAlertService,
        @Inject(TuiDialogService) private readonly dialogs: TuiDialogService,
        private readonly translate: TranslateService,
        private readonly breakpointObserver: BreakpointObserver,
        private readonly responsiveDialog: TuiResponsiveDialogService
    ) {
        this.columns = [
            {
                key: 'name',
                label: this.translate.instant('emailTemplates.name'),
                sortable: true,
                visible: true
            },
            {
                key: 'type',
                label: this.translate.instant('emailTemplates.type'),
                sortable: true,
                visible: true
            },
            {
                key: 'subject',
                label: this.translate.instant('emailTemplates.subject'),
                sortable: true,
                visible: true
            },
            {
                key: 'isActive',
                label: this.translate.instant('emailTemplates.status'),
                sortable: true,
                visible: true
            },
            {
                key: 'createdAt',
                label: this.translate.instant('emailTemplates.createdAt'),
                sortable: true,
                visible: true
            }
        ];
    }

    /**
     * Get badge appearance for active status
     */
    protected getActiveBadgeAppearance(isActive: boolean): 'success' | 'secondary' {
        return isActive ? 'success' : 'secondary';
    }

    /**
     * Get display text for template type
     */
    protected getTypeDisplayText(type: EmailTemplateType): string {
        return this.translate.instant(`emailTemplates.types.${type}`);
    }

    /**
     * Get formatted date
     */
    protected getFormattedDate(dateString: string): string {
        const date = new Date(dateString);
        return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    /**
     * Test email template
     */
    protected testEmailTemplate(template: UIEmailTemplate): void {
        this.emailTemplatesService.testEmailTemplate(template.id)
            .subscribe({
                next: (response) => {
                    this.alerts.open(
                        this.translate.instant('emailTemplates.actions.testSuccess', {
                            sentTo: response.sentTo
                        })
                    ).subscribe();
                },
                error: (error) => {
                    console.error('Error testing email template:', error);
                    this.alerts.open(
                        this.translate.instant('emailTemplates.actions.testError')
                    ).subscribe();
                }
            });
    }

    /**
     * Activate email template
     */
    protected activateEmailTemplate(template: UIEmailTemplate): void {
        this.emailTemplatesService.activateEmailTemplate(template.id)
            .subscribe({
                next: () => {
                    this.alerts.open(
                        this.translate.instant('emailTemplates.actions.activateSuccess')
                    ).subscribe();
                    // Refresh the list
                    this.refreshTemplates();
                },
                error: (error) => {
                    console.error('Error activating email template:', error);
                    this.alerts.open(
                        this.translate.instant('emailTemplates.actions.activateError')
                    ).subscribe();
                }
            });
    }

    /**
     * Delete email template
     */
    protected deleteEmailTemplate(template: UIEmailTemplate): void {
        const confirmData: TuiConfirmData = {
            content: this.translate.instant('emailTemplates.actions.deleteConfirm', {
                name: template.name
            }),
            yes: this.translate.instant('common.yes'),
            no: this.translate.instant('common.no')
        };

        this.dialogs.open<boolean>(TUI_CONFIRM, {
            label: this.translate.instant('emailTemplates.actions.deleteTemplate'),
            size: 'm',
            data: confirmData,
        }).subscribe((confirmed: boolean) => {
            if (confirmed) {
                this.emailTemplatesService.deleteEmailTemplate(template.id)
                    .subscribe({
                        next: () => {
                            this.alerts.open(
                                this.translate.instant('emailTemplates.actions.deleteSuccess')
                            ).subscribe();
                            // Refresh the list
                            this.refreshTemplates();
                        },
                        error: (error) => {
                            console.error('Error deleting email template:', error);
                            this.alerts.open(
                                this.translate.instant('emailTemplates.actions.deleteError')
                            ).subscribe();
                        }
                    });
            }
        });
    }

    /**
     * Refresh templates list
     */
    private refreshTemplates(): void {
        this.emailTemplatesService.getEmailTemplates()
            .subscribe({
                next: (templates) => {
                    this.emailTemplates = templates;
                },
                error: (error) => {
                    console.error('Error refreshing email templates:', error);
                }
            });
    }
}
