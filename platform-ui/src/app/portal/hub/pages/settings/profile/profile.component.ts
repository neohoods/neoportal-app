import { NgForOf } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiHint, TuiIcon, TuiTextfield } from '@taiga-ui/core';
import { TuiChevron, TuiDataListWrapper, TuiDataListWrapperComponent, TuiPassword, TuiSelect, TuiSwitch, TuiTooltip } from '@taiga-ui/kit';
import { AUTH_SERVICE_TOKEN } from '../../../../../global.provider';
import { UIPropertyType, UIUser, UIUserType } from '../../../../../models/UIUser';
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
    NgForOf,
    TuiButton,
    TuiPassword,
    TuiTextfield,
    TuiIcon,
    TuiSwitch,
    TuiTooltip,
    TuiHint,
    TuiDataListWrapperComponent,
    TranslateModule,
    FormsModule,
    TuiChevron,
    TuiDataListWrapper,
    TuiSelect,
    TuiTextfield
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent implements OnInit {
  profileForm: FormGroup;
  propertyTypes = Object.values(UIPropertyType);
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
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
    @Inject(PROFILE_SERVICE_TOKEN) private profileService: ProfileService,
  ) {
    this.profileForm = this.fb.group({
      username: ['', Validators.required],
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.minLength(6)]],
      type: [UIUserType.TENANT, Validators.required],
      flatNumber: [''],
      streetAddress: ['', Validators.required],
      city: ['', Validators.required],
      postalCode: ['', Validators.required],
      country: ['', Validators.required],
      preferredLanguage: ['en'],
      profileSharingConsent: [false],
    });
  }


  async ngOnInit(): Promise<void> {
    // Load configuration first
    await this.configService.loadConfig();
    this.config = await this.configService.getSettingsAsync();

    // Load profile after config is loaded
    this.loadProfile();
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
      type: this.user.type,
      flatNumber: this.user.flatNumber,
      streetAddress: this.user.streetAddress,
      city: this.user.city,
      postalCode: this.user.postalCode,
      country: this.user.country,
      preferredLanguage: this.user.preferredLanguage,
      profileSharingConsent: this.user.profileSharingConsent || false
    });
  }

  // Detect if SSO is enabled in configuration
  private detectSSOUser(): boolean {
    // Check if SSO is enabled in the configuration
    // If SSO is enabled, password management should be disabled for all users
    return this.config?.ssoEnabled || false;
  }

  onSave(): void {
    // Store current scroll position before validation
    const currentScrollY = window.scrollY;

    if (this.profileForm.valid) {
      const formValue = this.profileForm.value;

      this.user.username = formValue.username;
      this.user.firstName = formValue.firstName;
      this.user.lastName = formValue.lastName;
      this.user.email = formValue.email;
      this.user.type = formValue.type;
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
      });
    }
  }


}
