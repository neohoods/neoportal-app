import { CommonModule } from '@angular/common';
import { Component, Inject, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiDialogContext, TuiDialogService, TuiIcon, TuiTextfield } from '@taiga-ui/core';
import { TuiChip, TuiDataListWrapper, TuiInputPhone, TuiSwitch } from '@taiga-ui/kit';
import { TuiSelectModule } from '@taiga-ui/legacy';
import { Subject, takeUntil } from 'rxjs';
import { AUTH_SERVICE_TOKEN } from '../../global.provider';
import { UIUser, UIUserType } from '../../models/UIUser';
import { PROFILE_SERVICE_TOKEN, UNITS_SERVICE_TOKEN } from '../../portal/hub/hub.provider';
import { ProfileService } from '../../portal/hub/services/profile.service';
import { UnitsService } from '../../portal/hub/services/units.service';
import { AuthService } from '../../services/auth.service';

@Component({
    standalone: true,
    selector: 'app-profile-completion-check',
    imports: [
        CommonModule,
        TranslateModule,
        ReactiveFormsModule,
        FormsModule,
        TuiTextfield,
        TuiButton,
        TuiIcon,
        TuiChip,
        TuiSelectModule,
        TuiDataListWrapper,
        TuiInputPhone,
        TuiSwitch,
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

    profileForm: FormGroup;
    user: UIUser = {} as UIUser;
    availableUnits: any[] = [];
    loadingUnits = false;
    userTypes = Object.values(UIUserType).filter(type => type !== UIUserType.ADMIN);

    // Unit filtering
    selectedUnitType: any = null;
    unitSearchTerm = '';
    unitTypeOptions = [
        { value: null, label: 'directory.filters.type.all' },
        { value: 'FLAT', label: 'units.types.FLAT' },
        { value: 'GARAGE', label: 'units.types.GARAGE' },
        { value: 'PARKING', label: 'units.types.PARKING' },
        { value: 'COMMERCIAL', label: 'units.types.COMMERCIAL' },
        { value: 'OTHER', label: 'units.types.OTHER' },
    ];

    stringifyUnitType = (item: any): string => {
        if (!item) return '';
        if (item.label) {
            return this.translate.instant(item.label);
        }
        const option = this.unitTypeOptions.find(opt => opt.value === item);
        if (option) {
            return this.translate.instant(option.label);
        }
        return '';
    };

    getSelectedUnitTypeOption(): any {
        const currentType = this.selectedUnitType;
        if (!currentType) {
            return this.unitTypeOptions[0]; // Return "All types"
        }
        return this.unitTypeOptions.find(opt => opt.value === currentType) || this.unitTypeOptions[0];
    }

    onUnitTypeChange(selected: any): void {
        this.selectedUnitType = selected?.value || null;
        this.loadUnits();
    }

    onUnitSearchChange(): void {
        this.loadUnits();
    }

    // Stringify function for user type select
    readonly stringifyUserType = (userType: UIUserType): string => {
        return this.translate.instant(`user.type.${userType}`);
    };


    constructor(
        @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
        @Inject(PROFILE_SERVICE_TOKEN) private profileService: ProfileService,
        @Inject(UNITS_SERVICE_TOKEN) private unitsService: UnitsService,
        private dialogs: TuiDialogService,
        private router: Router,
        private alerts: TuiAlertService,
        private translate: TranslateService,
        private fb: FormBuilder
    ) {
        this.profileForm = this.fb.group({
            firstName: ['', Validators.required],
            lastName: ['', Validators.required],
            email: ['', [Validators.required, Validators.email]],
            phone: [''],
            type: [null, Validators.required],
            flatNumber: [''],
            streetAddress: ['', Validators.required],
            city: ['', Validators.required],
            postalCode: ['', Validators.required],
            country: ['', Validators.required],
            preferredLanguage: ['en'],
            profileSharingConsent: [true], // Enable by default
        });
    }

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
                    this.user = user;
                    this.missingFields = this.getMissingFields(user);

                    // Load form with user data
                    this.profileForm.patchValue({
                        firstName: user.firstName || '',
                        lastName: user.lastName || '',
                        email: user.email || '',
                        phone: user.phone || '',
                        type: user.type || null,
                        flatNumber: user.flatNumber || '',
                        streetAddress: user.streetAddress || '',
                        city: user.city || '',
                        postalCode: user.postalCode || '',
                        country: user.country || '',
                        preferredLanguage: user.preferredLanguage || 'en',
                        profileSharingConsent: user.profileSharingConsent || false,
                    });

                    // Redirect to profile page if profile is incomplete and not already there
                    if (this.missingFields.length > 0) {
                        const currentUrl = this.router.url;
                        if (!currentUrl.includes('/hub/settings/profile')) {
                            this.router.navigate(['/hub/settings/profile']);
                        }
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
        }
        // Properties check removed - properties concept has been removed

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

        // Check if user.type is missing - if so, don't allow dismiss
        const canDismiss = this.user.type != null;

        this.dialogs
            .open(this.dialogTemplate, {
                label: `${this.translate.instant('profileCompletion.title')}`,
                size: 'l',
                closeable: canDismiss,
                dismissible: canDismiss,
            })
            .subscribe({
                next: () => {
                    // Dialog was closed - check if we should save or dismiss
                    if (this.profileForm.valid) {
                        this.saveProfile();
                    } else {
                        this.dismissDialog();
                    }
                },
                error: () => {
                    // Dialog was dismissed
                    this.dismissDialog();
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

    dismissDialog(context?: TuiDialogContext<void>): void {
        // Only allow dismiss if user.type is defined
        if (this.user.type == null) {
            return; // Cannot dismiss if user.type is not set
        }
        if (context) {
            context.completeWith(undefined);
        }
        this.dismissed = true; // Don't show again in this session
    }

    saveProfile(context?: TuiDialogContext<void>): void {
        if (this.profileForm.valid) {
            const formValue = this.profileForm.value;

            // Update user object with form values
            this.user.firstName = formValue.firstName;
            this.user.lastName = formValue.lastName;
            this.user.email = formValue.email;
            this.user.phone = formValue.phone;
            // Only update type if it's not already set (first time setting it)
            if (!this.user.type) {
                this.user.type = formValue.type;
            }
            this.user.flatNumber = formValue.flatNumber;
            this.user.streetAddress = formValue.streetAddress;
            this.user.city = formValue.city;
            this.user.postalCode = formValue.postalCode;
            this.user.country = formValue.country;
            this.user.preferredLanguage = formValue.preferredLanguage;
            this.user.profileSharingConsent = formValue.profileSharingConsent;

            this.profileService.updateProfile(this.user)
                .pipe(takeUntil(this.destroy$))
                .subscribe({
                    next: (updatedUser: UIUser) => {
                        this.user = updatedUser;
                        this.alerts
                            .open(this.translate.instant('profileCompletion.saveSuccess'), {
                                appearance: 'positive',
                            })
                            .subscribe();
                        // Close dialog and refresh profile check
                        if (context) {
                            context.completeWith(undefined);
                        }
                        this.checkProfileCompletion();
                    },
                    error: (error) => {
                        console.error('Error saving profile:', error);
                        this.alerts
                            .open(this.translate.instant('profileCompletion.saveError'), {
                                appearance: 'negative',
                            })
                            .subscribe();
                    }
                });
        }
    }

    private loadUnits(): void {
        this.loadingUnits = true;
        this.unitsService.getUnitsDirectory(
            0,
            100,
            this.selectedUnitType || undefined,
            this.unitSearchTerm || undefined,
            undefined // Don't filter by user
        )
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: (paginated: any) => {
                    this.availableUnits = paginated.content || [];
                    this.loadingUnits = false;
                },
                error: (error) => {
                    console.error('Error loading units:', error);
                    this.loadingUnits = false;
                }
            });
    }

    joinUnit(unitId: string): void {
        this.unitsService.createJoinRequest(unitId)
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: () => {
                    this.alerts
                        .open(this.translate.instant('profileCompletion.joinUnitSuccess'), {
                            appearance: 'positive',
                        })
                        .subscribe();
                    // Reload units to update status
                    this.loadUnits();
                },
                error: (error) => {
                    console.error('Error joining unit:', error);
                    this.alerts
                        .open(this.translate.instant('profileCompletion.joinUnitError'), {
                            appearance: 'negative',
                        })
                        .subscribe();
                }
            });
    }
}
