import { NgForOf, NgIf } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiDay } from '@taiga-ui/cdk';
import { TuiAlertService, TuiButton, TuiNotification, TuiTextfield } from '@taiga-ui/core';
import { TuiTabs } from '@taiga-ui/kit';
import { TuiInputDateModule } from '@taiga-ui/legacy';
import { UIInfo } from '../../../../models/UIInfos';
import { INFOS_SERVICE_TOKEN } from '../../admin.providers';
import { InfosService } from '../../services/infos.service';

@Component({
  standalone: true,
  selector: 'app-infos',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    NgForOf,
    NgIf,
    TuiButton,
    TranslateModule,
    TuiTextfield,
    TuiInputDateModule,
    TuiTabs,
    TuiNotification
  ],
  templateUrl: './infos.component.html',
  styleUrl: './infos.component.scss',
})
export class InfosComponent implements OnInit {

  activeTabIndex = 0;

  infos: UIInfo = {
    id: '1',
    nextAGDate: '',
    rulesUrl: '',
    delegates: [],
    contactNumbers: {
      syndic: {
        type: '',
        description: '',
        availability: '',
        responseTime: '',
        name: '',
        phoneNumber: '',
        email: '',
        officeHours: '',
        address: '',
      },
      emergency: [],
    }
  };
  editInfosForm: FormGroup;


  constructor(
    @Inject(INFOS_SERVICE_TOKEN)
    private infosService: InfosService,
    private fb: FormBuilder,
    private alerts: TuiAlertService,
    private translate: TranslateService,
  ) {
    this.editInfosForm = this.fb.group({
      nextAGDate: [''],
      rulesUrl: [''],
      delegates: this.fb.array([]),
      contactNumbers: this.fb.group({
        syndic: this.createContactNumberFormGroup(),
        emergency: this.fb.array([])
      })
    });
  }

  ngOnInit(): void {
    this.loadInfos();
  }

  private loadInfos(): void {
    this.infosService
      .getInfos()
      .subscribe((infos) => {
        this.infos = infos;
        this.editInfosForm.patchValue({
          nextAGDate: infos.nextAGDate ? this.stringToTuiDay(infos.nextAGDate) : null,
          rulesUrl: infos.rulesUrl
        });
        this.setDelegates(infos.delegates);
        this.setContactNumbers(infos.contactNumbers);
      });
  }

  get delegates(): FormArray {
    return this.editInfosForm.get('delegates') as FormArray;
  }

  get emergencyContacts(): FormArray {
    return this.editInfosForm.get('contactNumbers.emergency') as FormArray;
  }

  get syndicContact(): FormGroup {
    return this.editInfosForm.get('contactNumbers.syndic') as FormGroup;
  }

  createDelegateFormGroup() {
    return this.fb.group({
      building: [''],
      firstName: [''],
      lastName: [''],
      email: [''],
      matrixUser: ['']
    });
  }

  createContactNumberFormGroup() {
    return this.fb.group({
      type: [''],
      description: [''],
      availability: [''],
      responseTime: [''],
      name: [''],
      phoneNumber: [''],
      email: [''],
      officeHours: [''],
      address: ['']
    });
  }

  setDelegates(delegates: any[]) {
    const delegateFormArray = this.delegates;
    delegateFormArray.clear();
    delegates.forEach(delegate => {
      const delegateGroup = this.createDelegateFormGroup();
      delegateGroup.patchValue(delegate);
      delegateFormArray.push(delegateGroup);
    });
  }

  setContactNumbers(contactNumbers: any) {
    // Set syndic contact
    if (contactNumbers.syndic) {
      this.syndicContact.patchValue(contactNumbers.syndic);
    }

    // Set emergency contacts
    const emergencyArray = this.emergencyContacts;
    emergencyArray.clear();
    if (contactNumbers.emergency && contactNumbers.emergency.length > 0) {
      contactNumbers.emergency.forEach((emergency: any) => {
        const emergencyGroup = this.createContactNumberFormGroup();
        emergencyGroup.patchValue(emergency);
        emergencyArray.push(emergencyGroup);
      });
    }
  }

  addDelegate() {
    this.delegates.push(this.createDelegateFormGroup());
  }

  removeDelegate(index: number) {
    this.delegates.removeAt(index);
  }

  addEmergencyContact() {
    this.emergencyContacts.push(this.createContactNumberFormGroup());
  }

  removeEmergencyContact(index: number) {
    this.emergencyContacts.removeAt(index);
  }

  private stringToTuiDay(dateString: string): TuiDay | null {
    if (!dateString) return null;
    try {
      const date = new Date(dateString);
      return TuiDay.fromLocalNativeDate(date);
    } catch {
      return null;
    }
  }

  private tuiDayToString(tuiDay: TuiDay | null): string {
    if (!tuiDay) return '';
    return tuiDay.toLocalNativeDate().toISOString().split('T')[0];
  }

  onSubmit() {
    const formValue = this.editInfosForm.value;
    const updatedInfos = {
      ...this.infos,
      nextAGDate: this.tuiDayToString(formValue.nextAGDate),
      rulesUrl: formValue.rulesUrl,
      delegates: formValue.delegates,
      contactNumbers: formValue.contactNumbers
    };

    this.infosService
      .updateInfos(updatedInfos)
      .subscribe(() => {
        this.alerts
          .open(this.translate.instant('infos.updateSuccess') || 'Community information updated successfully', {
            appearance: 'positive',
          })
          .subscribe();
      });
  }
}
