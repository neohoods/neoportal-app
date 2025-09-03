import { NgForOf } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiNotification, TuiTextfield } from '@taiga-ui/core';
import { TuiPassword } from '@taiga-ui/kit';
import { TuiSelectModule } from '@taiga-ui/legacy';
import { AUTH_SERVICE_TOKEN } from '../../../../../global.provider';
import { UIProperty, UIPropertyType, UIUser } from '../../../../../models/UIUser';
import { AuthService } from '../../../../../services/auth.service';
import { PROFILE_SERVICE_TOKEN } from '../../../hub.provider';
import { ProfileService } from '../../../services/profile.service';

@Component({
  selector: 'app-properties',
  imports: [
    ReactiveFormsModule,
    NgForOf,
    TuiButton,
    TuiPassword,
    TuiTextfield,
    TuiIcon,
    TuiSelectModule,
    TranslateModule,
    TuiNotification
  ],
  templateUrl: './properties.component.html',
  styleUrl: './properties.component.scss'
})
export class PropertiesComponent implements OnInit {
  profileForm: FormGroup;
  propertyTypes = Object.values(UIPropertyType);
  user: UIUser = {} as UIUser;

  // Stringify function for property type select
  readonly stringifyPropertyType = (propertyType: UIPropertyType): string => {
    return this.translate.instant(`property.type.${propertyType}`);
  };

  constructor(
    private fb: FormBuilder,
    private alerts: TuiAlertService,
    private translate: TranslateService,
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
    @Inject(PROFILE_SERVICE_TOKEN) private profileService: ProfileService,
  ) {
    this.profileForm = this.fb.group({
      properties: this.fb.array([])
    });
  }

  get properties(): FormArray {
    return this.profileForm.get('properties') as FormArray;
  }

  ngOnInit(): void {
    this.loadProfile();
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

  // Load initial profile data
  loadProfile(): void {
    this.user = this.authService.getCurrentUserInfo().user;

    this.setProperties(this.user.properties || []);
  }

  onSave(): void {
    if (this.profileForm.valid) {
      const formValue = this.profileForm.value;
      this.user.properties = formValue.properties;

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
