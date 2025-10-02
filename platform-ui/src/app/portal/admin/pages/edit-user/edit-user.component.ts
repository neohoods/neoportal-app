import { NgForOf, NgIf } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import {
  FormArray,
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
import { TuiPassword } from '@taiga-ui/kit';
import { TuiSelectModule } from '@taiga-ui/legacy';
import { UIProperty, UIPropertyType, UIUser, UIUserType } from '../../../../models/UIUser';
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
    NgIf,
    TuiButton,
    TuiTextfield,
    TuiIcon,
    TuiPassword,
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
  propertyTypes = Object.values(UIPropertyType);

  // Stringify function for user type select
  readonly stringifyUserType = (userType: UIUserType): string => {
    return this.translate.instant(`user.type.${userType}`);
  };

  // Stringify function for property type select
  readonly stringifyPropertyType = (propertyType: UIPropertyType): string => {
    return this.translate.instant(`property.type.${propertyType}`);
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
      properties: this.fb.array([])
    });
  }

  get properties(): FormArray {
    return this.editUserForm.get('properties') as FormArray;
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
          avatarUrl: user.avatarUrl
        });
        this.setProperties(user.properties || []);
      });
    } else {
      // Add password field for new users
      this.editUserForm.addControl('password', this.fb.control('', Validators.required));
    }
  }

  createPropertyFormGroup(): FormGroup {
    return this.fb.group({
      name: ['', Validators.required],
      type: [UIPropertyType.APARTMENT, Validators.required]
    });
  }

  setProperties(properties: UIProperty[]) {
    const propertyFormArray = this.properties;
    propertyFormArray.clear();
    properties.forEach(property => {
      const propertyGroup = this.createPropertyFormGroup();
      propertyGroup.patchValue(property);
      propertyFormArray.push(propertyGroup);
    });
  }

  addProperty() {
    this.properties.push(this.createPropertyFormGroup());
  }

  removeProperty(index: number) {
    this.properties.removeAt(index);
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
        properties: formValue.properties,
        flatNumber: formValue.flatNumber,
        streetAddress: formValue.streetAddress,
        city: formValue.city,
        postalCode: formValue.postalCode,
        country: formValue.country,
        avatarUrl: formValue.avatarUrl,
        preferredLanguage: formValue.preferredLanguage
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
