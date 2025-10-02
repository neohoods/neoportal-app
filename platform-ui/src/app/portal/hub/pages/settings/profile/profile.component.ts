import { NgForOf } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiHint, TuiIcon, TuiTextfield } from '@taiga-ui/core';
import { TuiDataListWrapperComponent, TuiPassword, TuiSwitch, TuiTooltip } from '@taiga-ui/kit';
import { TuiSelectModule } from '@taiga-ui/legacy';
import { AUTH_SERVICE_TOKEN } from '../../../../../global.provider';
import { UIPropertyType, UIUser, UIUserType } from '../../../../../models/UIUser';
import { AuthService } from '../../../../../services/auth.service';
import { PROFILE_SERVICE_TOKEN } from '../../../hub.provider';
import { ProfileService } from '../../../services/profile.service';

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
    TuiSelectModule,
    TuiDataListWrapperComponent,
    TranslateModule
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


  ngOnInit(): void {
    this.loadProfile();
  }

  // Load initial profile data
  loadProfile(): void {
    this.user = this.authService.getCurrentUserInfo().user;
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

  onSave(): void {
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
