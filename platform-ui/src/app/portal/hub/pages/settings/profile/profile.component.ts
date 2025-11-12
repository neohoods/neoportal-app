import { Component, Inject, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiHint, TuiIcon, TuiNotification, TuiTextfield } from '@taiga-ui/core';
import { TuiChevron, TuiDataListWrapper, TuiDataListWrapperComponent, TuiInputPhone, TuiPassword, TuiSelect, TuiSwitch, TuiTooltip } from '@taiga-ui/kit';
import { AUTH_SERVICE_TOKEN } from '../../../../../global.provider';
import { UIUser, UIUserType } from '../../../../../models/UIUser';
import { AuthService } from '../../../../../services/auth.service';
import { ConfigService, UISettings } from '../../../../../services/config.service';
import { PROFILE_SERVICE_TOKEN } from '../../../hub.provider';
import { ProfileService } from '../../../services/profile.service';

interface Character {
  readonly id: number;
  readonly name: string;
}

@Component({
  selector: 'app-profile',
  imports: [
    ReactiveFormsModule,
    RouterModule,
    TuiButton,
    TuiPassword,
    TuiTextfield,
    TuiIcon,
    TuiSwitch,
    TuiTooltip,
    TuiHint,
    TuiNotification,
    TuiDataListWrapperComponent,
    TranslateModule,
    FormsModule,
    TuiChevron,
    TuiDataListWrapper,
    TuiSelect,
    TuiInputPhone,
    TuiTextfield
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent implements OnInit {
  profileForm: FormGroup;
  // Filter out ADMIN from user types - users cannot promote themselves to admin
  userTypes = Object.values(UIUserType).filter(type => type !== UIUserType.ADMIN);
  user: UIUser = {} as UIUser;
  isSSOUser = false; // Flag to track if user is SSO authenticated
  config: UISettings | null = null;

  // Stringify function for user type select
  readonly stringifyUserType = (userType: UIUserType): string => {
    return this.translate.instant(`user.type.${userType}`);
  };

  // Getter for type FormControl
  get typeControl() {
    return this.profileForm.get('type')!;
  }

  constructor(
    private fb: FormBuilder,
    private alerts: TuiAlertService,
    private translate: TranslateService,
    private configService: ConfigService,
    private router: Router,
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
    @Inject(PROFILE_SERVICE_TOKEN) private profileService: ProfileService,
  ) {
    this.profileForm = this.fb.group({
      username: ['', Validators.required],
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      password: ['', [Validators.minLength(6)]],
      type: [null, Validators.required],
      flatNumber: [''],
      streetAddress: ['', Validators.required],
      city: ['', Validators.required],
      postalCode: ['', Validators.required],
      country: ['', Validators.required],
      preferredLanguage: ['en'],
      profileSharingConsent: [false],
    });
  }


  profileIncomplete = false;
  missingFields: string[] = [];
  hasNoUnit = false;

  async ngOnInit(): Promise<void> {
    // Load configuration first
    await this.configService.loadConfig();
    this.config = await this.configService.getSettingsAsync();

    // Load profile after config is loaded
    this.loadProfile();

    // Check if profile is complete
    this.checkProfileCompletion();
  }

  private checkProfileCompletion(): void {
    const user = this.authService.getCurrentUserInfo()?.user;
    if (!user) {
      return;
    }

    this.missingFields = this.getMissingFields(user);
    this.profileIncomplete = this.missingFields.length > 0;
    this.hasNoUnit = !user.primaryUnitId;
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

  // Load initial profile data
  loadProfile(): void {
    this.user = this.authService.getCurrentUserInfo().user;

    // Detect if user is SSO authenticated
    // For now, we'll use a simple heuristic: if the user has no password field or specific SSO indicators
    this.isSSOUser = this.detectSSOUser();

    this.profileForm.patchValue({
      username: this.user.username,
      firstName: this.user.firstName,
      lastName: this.user.lastName,
      email: this.user.email,
      phone: this.user.phone || '',
      type: this.user.type,
      flatNumber: this.user.flatNumber,
      streetAddress: this.user.streetAddress,
      city: this.user.city,
      postalCode: this.user.postalCode,
      country: this.user.country,
      preferredLanguage: this.user.preferredLanguage,
      profileSharingConsent: this.user.profileSharingConsent || false
    });

    // Mark type field as touched if it's empty to show error state (red border)
    if (!this.user.type) {
      this.profileForm.get('type')?.markAsTouched();
    }
  }

  // Detect if SSO is enabled in configuration
  private detectSSOUser(): boolean {
    // Check if SSO is enabled in the configuration
    // If SSO is enabled, password management should be disabled for all users
    return this.config?.ssoEnabled || false;
  }

  // Get validation errors for the form
  getFormValidationErrors(): string[] {
    const errors: string[] = [];
    const fieldLabels: { [key: string]: string } = {
      username: this.translate.instant('profile.username'),
      firstName: this.translate.instant('profile.firstName'),
      lastName: this.translate.instant('profile.lastName'),
      email: this.translate.instant('profile.email'),
      type: this.translate.instant('profile.type'),
      streetAddress: this.translate.instant('profile.streetAddress'),
      city: this.translate.instant('profile.city'),
      postalCode: this.translate.instant('profile.postalCode'),
      country: this.translate.instant('profile.country'),
      password: this.translate.instant('profile.password'),
    };

    Object.keys(this.profileForm.controls).forEach(key => {
      const control = this.profileForm.get(key);
      if (control && control.invalid) {
        const fieldLabel = fieldLabels[key] || key;
        if (control.errors?.['required']) {
          errors.push(`${fieldLabel}: ${this.translate.instant('profile.validation.fieldRequired')}`);
        } else if (control.errors?.['email']) {
          errors.push(`${fieldLabel}: ${this.translate.instant('profile.validation.invalidEmail')}`);
        } else if (control.errors?.['minlength']) {
          errors.push(`${fieldLabel}: ${this.translate.instant('profile.validation.fieldMinLength', { min: control.errors['minlength'].requiredLength })}`);
        }
      }
    });

    return errors;
  }

  // Get tooltip text for save button when form is invalid
  getSaveButtonTooltip(): string {
    const errors = this.getFormValidationErrors();
    if (errors.length === 0) {
      return '';
    }
    return this.translate.instant('profile.validation.errors') + ':\n' + errors.join('\n');
  }

  onSave(): void {
    // Mark all fields as touched to show validation errors
    Object.keys(this.profileForm.controls).forEach(key => {
      this.profileForm.get(key)?.markAsTouched();
    });

    if (!this.profileForm.valid) {
      const errors = this.getFormValidationErrors();
      if (errors.length > 0) {
        this.alerts
          .open(
            this.translate.instant('profile.validation.formInvalid') + ':\n\n' + errors.join('\n'),
            {
              appearance: 'error',
              label: this.translate.instant('profile.validation.title'),
            }
          )
          .subscribe();
      }
      return;
    }

    if (this.profileForm.valid) {
      const formValue = this.profileForm.value;

      this.user.username = formValue.username;
      this.user.firstName = formValue.firstName;
      this.user.lastName = formValue.lastName;
      this.user.email = formValue.email;
      this.user.phone = formValue.phone;
      // Only update type if it's not already set (first time setting it)
      if (!this.user.type) {
        this.user.type = formValue.type || null;
      }
      this.user.flatNumber = formValue.flatNumber;
      this.user.streetAddress = formValue.streetAddress;
      this.user.city = formValue.city;
      this.user.postalCode = formValue.postalCode;
      this.user.country = formValue.country;
      this.user.preferredLanguage = formValue.preferredLanguage;
      this.user.profileSharingConsent = formValue.profileSharingConsent;

      // Only include password if user is not SSO and password is provided
      if (!this.isSSOUser && formValue.password && formValue.password.trim()) {
        (this.user as any).password = formValue.password;
      }

      this.profileService.updateProfile(this.user).subscribe(() => {
        this.alerts
          .open(this.translate.instant('profile.saveSuccess'), {
            appearance: 'positive',
          })
          .subscribe();
        // Reload profile and check completion
        this.loadProfile();
        this.checkProfileCompletion();

        // If user has no unit, redirect to units page after profile reload
        // Check again after reload to get the latest user data
        const hasNoUnit = !this.user.primaryUnitId;
        if (hasNoUnit) {
          setTimeout(() => {
            this.router.navigate(['/hub/settings/units']);
          }, 500); // Small delay to let the success message show
        }
      });
    }
  }


}
