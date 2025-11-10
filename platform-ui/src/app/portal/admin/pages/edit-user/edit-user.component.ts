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
import { TuiInputPhone, TuiPassword } from '@taiga-ui/kit';
import { TuiSelectModule } from '@taiga-ui/legacy';
import { UIUser, UIUserType } from '../../../../models/UIUser';
import { USERS_SERVICE_TOKEN } from '../../admin.providers';
import { UsersService } from '../../services/users.service';
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

  userTypes = Object.values(UIUserType);

  // Stringify function for user type select
  readonly stringifyUserType = (userType: UIUserType): string => {
    return this.translate.instant(`user.type.${userType}`);
  };

  constructor(
    private route: ActivatedRoute,
    @Inject(USERS_SERVICE_TOKEN) private usersService: UsersService,
    private fb: FormBuilder,
    private alerts: TuiAlertService,
    private router: Router,
    private translate: TranslateService,
  ) {
    this.editUserForm = this.fb.group({
      username: ['', Validators.required],
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      flatNumber: [''],
      streetAddress: ['', Validators.required],
      city: ['', Validators.required],
      postalCode: ['', Validators.required],
      country: ['', Validators.required],
      type: [UIUserType.TENANT, Validators.required],
      roles: [['hub']],
      disabled: [false],
      isEmailVerified: [false],
      preferredLanguage: ['en'],
      avatarUrl: [''],
      phoneNumber: ['']
    });
  }

  ngOnInit() {
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
          phoneNumber: user.phone || ''
        });
      });
    } else {
      // Add password field for new users
      this.editUserForm.addControl('password', this.fb.control('', Validators.required));
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
        createdAt: this.user.createdAt || new Date().toISOString()
      };

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
