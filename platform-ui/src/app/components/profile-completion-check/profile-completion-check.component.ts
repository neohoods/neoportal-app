import { CommonModule } from '@angular/common';
import { Component, Inject, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiResponsiveDialogService } from '@taiga-ui/addon-mobile';
import { TuiAlertService } from '@taiga-ui/core';
import { TUI_CONFIRM, TuiConfirmData } from '@taiga-ui/kit';
import { Subject, takeUntil } from 'rxjs';
import { AUTH_SERVICE_TOKEN } from '../../global.provider';
import { UIUser, UIUserType } from '../../models/UIUser';
import { AuthService } from '../../services/auth.service';

@Component({
    standalone: true,
    selector: 'app-profile-completion-check',
    imports: [
        CommonModule,
        TranslateModule,
    ],
    templateUrl: './profile-completion-check.component.html',
    styleUrls: ['./profile-completion-check.component.scss']
})
export class ProfileCompletionCheckComponent implements OnInit, OnDestroy {
    @ViewChild('dialogTemplate', { static: true }) dialogTemplate!: TemplateRef<any>;

    missingFields: string[] = [];
    hasProperties: boolean = false;
    private destroy$ = new Subject<void>();
    private dismissed = false;

    constructor(
        @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
        private dialogs: TuiResponsiveDialogService,
        private router: Router,
        private alerts: TuiAlertService,
        private translate: TranslateService
    ) { }

    ngOnInit(): void {
        // Check profile completion when component initializes
        this.checkProfileCompletion();
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    private checkProfileCompletion(): void {
        // Don't show if already dismissed in this session
        if (this.dismissed) {
            return;
        }

        this.authService.getUserProfile()
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: (user: UIUser) => {
                    this.missingFields = this.getMissingFields(user);

                    // Show dialog if profile is incomplete
                    if (this.missingFields.length > 0) {
                        this.showProfileCompletionDialog();
                    }
                },
                error: (error) => {
                    console.error('Error checking profile completion:', error);
                }
            });
    }

    private getMissingFields(user: UIUser): string[] {
        const missing: string[] = [];

        // Check required fields
        if (!user.firstName?.trim()) {
            missing.push('firstName');
        }
        if (!user.lastName?.trim()) {
            missing.push('lastName');
        }
        if (!user.email?.trim()) {
            missing.push('email');
        }
        if (!user.type) {
            missing.push('type');
        } else if (user.properties && user.properties.length === 0) {
            if (user.type === UIUserType.OWNER) {
                missing.push('properties');
            } else if (user.type === UIUserType.LANDLORD) {
                missing.push('properties');
            } else if (user.type === UIUserType.SYNDIC) {
                missing.push('properties');
            } else if (user.type === UIUserType.TENANT) {
                missing.push('properties');
            }
        }

        // Check address fields
        if (!user.streetAddress?.trim()) {
            missing.push('streetAddress');
        }
        if (!user.city?.trim()) {
            missing.push('city');
        }
        if (!user.postalCode?.trim()) {
            missing.push('postalCode');
        }
        if (!user.country?.trim()) {
            missing.push('country');
        }


        return missing;
    }

    private showProfileCompletionDialog(): void {
        this.hasProperties = this.missingFields.includes('properties');

        const data: TuiConfirmData = {
            content: this.dialogTemplate,
            yes: `✨ ${this.translate.instant('profileCompletion.completeProfile')}`,
            no: this.translate.instant('profileCompletion.later'),
        };

        this.dialogs
            .open<boolean>(TUI_CONFIRM, {
                label: `${this.translate.instant('profileCompletion.title')}`,
                size: 'm',
                data,
            })
            .subscribe({
                next: (confirmed: boolean) => {
                    if (confirmed) {
                        this.navigateToCompletion();
                    } else {
                        this.dismissDialog();
                    }
                }
            });
    }

    private navigateToCompletion(): void {
        // If properties are missing, go to properties page
        if (this.missingFields.includes('properties')) {
            this.router.navigate(['/hub/settings/properties']);
        } else {
            // Otherwise go to profile page
            this.router.navigate(['/hub/settings/profile']);
        }
    }

    private dismissDialog(): void {
        this.dismissed = true; // Don't show again in this session
    }
}
