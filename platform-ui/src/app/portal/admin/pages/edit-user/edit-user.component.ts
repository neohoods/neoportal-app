import { NgForOf } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import {
  TuiAlertService,
  TuiButton,
  TuiIcon,
  TuiTextfield,
} from '@taiga-ui/core';
import { TuiInputPhone, TuiPassword, TuiSwitch } from '@taiga-ui/kit';
import { TuiSelectModule } from '@taiga-ui/legacy';
import { UIUser, UIUserType } from '../../../../models/UIUser';
import { USERS_SERVICE_TOKEN } from '../../admin.providers';
import { UsersService } from '../../services/users.service';
import { ConfigService, UISettings } from '../../../../services/config.service';
@Component({
  standalone: true,
  selector: 'app-edit-user',
  imports: [
    RouterLink,
    TuiButton,
    FormsModule,
    ReactiveFormsModule,
    NgForOf,
    TuiButton,
    TuiTextfield,
    TuiIcon,
    TuiPassword,
    TuiInputPhone,
    TuiSwitch,
    TuiSelectModule,
    TranslateModule
  ],
  templateUrl: './edit-user.component.html',
  styleUrl: './edit-user.component.scss',
})
export class EditUserComponent implements OnInit {
  user: UIUser = {} as UIUser;
  editUserForm: FormGroup;
  userId: string | null = null;
  isSSOEnabled = false;
  config: UISettings | null = null;

  userTypes = Object.values(UIUserType);

  // Stringify function for user type select
  readonly stringifyUserType = (userType: UIUserType): string => {
    return this.translate.instant(`user.type.${userType}`);
  };

  // Generate a random password for SSO users (required by DB but won't be used for auth)
  private generateRandomPassword(): string {
    const length = 32;
    const charset = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*';
    let password = '';
    const array = new Uint8Array(length);
    crypto.getRandomValues(array);
    for (let i = 0; i < length; i++) {
      password += charset[array[i] % charset.length];
    }
    return password;
  }

  constructor(
    private route: ActivatedRoute,
    @Inject(USERS_SERVICE_TOKEN) private usersService: UsersService,
    private fb: FormBuilder,
    private alerts: TuiAlertService,
    private router: Router,
    private translate: TranslateService,
    private configService: ConfigService,
  ) {
    this.editUserForm = this.fb.group({
      username: ['', Validators.required],
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      flatNumber: [''],
      streetAddress: [''],
      city: [''],
      postalCode: [''],
      country: [''],
      type: [UIUserType.TENANT, Validators.required],
      roles: [['hub']],
      disabled: [false],
      isEmailVerified: [false],
      preferredLanguage: ['en'],
      avatarUrl: [''],
      phoneNumber: [''],
      profileSharingConsent: [false]
    });
  }

  async ngOnInit() {
    // Load configuration first
    await this.configService.loadConfig();
    this.config = await this.configService.getSettingsAsync();
    this.isSSOEnabled = this.config?.ssoEnabled || false;

    this.userId = this.route.snapshot.paramMap.get('id');
    if (this.userId) {
      this.usersService.getUser(this.userId).subscribe((user) => {
        this.user = user;
        this.editUserForm.patchValue({
          username: user.username,
          firstName: user.firstName,
          lastName: user.lastName,
          email: user.email,
          flatNumber: user.flatNumber,
          streetAddress: user.streetAddress,
          city: user.city,
          postalCode: user.postalCode,
          country: user.country,
          type: user.type,
          roles: user.roles,
          disabled: user.disabled,
          isEmailVerified: user.isEmailVerified,
          preferredLanguage: user.preferredLanguage,
          avatarUrl: user.avatarUrl,
          phoneNumber: user.phone || '',
          profileSharingConsent: user.profileSharingConsent || false
        });
      });
    } else {
      // Add password field for new users only if SSO is not enabled
      if (!this.isSSOEnabled) {
        this.editUserForm.addControl('password', this.fb.control('', Validators.required));
      }
    }
  }

  onSubmit() {
    if (this.editUserForm.valid) {
      const formValue = this.editUserForm.value;
      const updatedUser: UIUser = {
        id: this.user.id || '',
        username: formValue.username,
        firstName: formValue.firstName,
        lastName: formValue.lastName,
        email: formValue.email,
        isEmailVerified: formValue.isEmailVerified,
        disabled: formValue.disabled,
        type: formValue.type,
        roles: formValue.roles,
        flatNumber: formValue.flatNumber,
        streetAddress: formValue.streetAddress,
        city: formValue.city,
        postalCode: formValue.postalCode,
        country: formValue.country,
        avatarUrl: formValue.avatarUrl,
        preferredLanguage: formValue.preferredLanguage,
        phone: formValue.phoneNumber,
        profileSharingConsent: formValue.profileSharingConsent || false,
        createdAt: this.user.createdAt || new Date().toISOString()
      };

      // Handle password: 
      // - For new users: always generate a random password if not provided (required by DB)
      // - For existing users: only update if password is provided and SSO is not enabled
      if (!this.userId) {
        // Creating new user - always ensure a password is set
        if (formValue.password && formValue.password.trim()) {
          (updatedUser as any).password = formValue.password;
        } else {
          // Generate a random password (required by DB, won't be used if SSO is enabled)
          const randomPassword = this.generateRandomPassword();
          (updatedUser as any).password = randomPassword;
        }
      } else {
        // Updating existing user - only update password if provided and SSO is not enabled
        if (!this.isSSOEnabled && formValue.password && formValue.password.trim()) {
          (updatedUser as any).password = formValue.password;
        }
      }

      this.usersService.saveUser(updatedUser).subscribe(() => {
        this.alerts
          .open(`Successfully saved ${updatedUser.username}`, {
            appearance: 'positive',
          })
          .subscribe();
        this.router.navigate(['/admin/users']);
      });
    }
  }
}
