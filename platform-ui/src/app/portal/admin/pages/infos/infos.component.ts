import { NgForOf, NgIf } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiDay } from '@taiga-ui/cdk';
import { TuiAlertService, TuiButton, TuiNotification, TuiTextfield } from '@taiga-ui/core';
import { TuiDataListWrapper, TuiSelect, TuiTabs } from '@taiga-ui/kit';
import { TuiInputDateModule } from '@taiga-ui/legacy';
import { UIContactNumber, UIInfo } from '../../../../models/UIInfos';
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
    TuiNotification,
    TuiSelect,
    TuiDataListWrapper
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
    contactNumbers: []
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
      syndicContacts: this.fb.array([]),
      emergencyContacts: this.fb.array([]),
      maintenanceContacts: this.fb.array([])
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

  get syndicContacts(): FormArray {
    return this.editInfosForm.get('syndicContacts') as FormArray;
  }

  get emergencyContacts(): FormArray {
    return this.editInfosForm.get('emergencyContacts') as FormArray;
  }

  get maintenanceContacts(): FormArray {
    return this.editInfosForm.get('maintenanceContacts') as FormArray;
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

  setContactNumbers(contactNumbers: UIContactNumber[]) {
    // Clear all contact arrays
    this.syndicContacts.clear();
    this.emergencyContacts.clear();
    this.maintenanceContacts.clear();

    if (contactNumbers && contactNumbers.length > 0) {
      contactNumbers.forEach((contact: UIContactNumber) => {
        const contactGroup = this.createContactNumberFormGroup();
        contactGroup.patchValue(contact);

        switch (contact.contactType) {
          case 'syndic':
            this.syndicContacts.push(contactGroup);
            break;
          case 'emergency':
            this.emergencyContacts.push(contactGroup);
            break;
          case 'maintenance':
            this.maintenanceContacts.push(contactGroup);
            break;
        }
      });
    }
  }

  addDelegate() {
    this.delegates.push(this.createDelegateFormGroup());
  }

  removeDelegate(index: number) {
    this.delegates.removeAt(index);
  }

  addSyndicContact() {
    this.syndicContacts.push(this.createContactNumberFormGroup());
  }

  addEmergencyContact() {
    this.emergencyContacts.push(this.createContactNumberFormGroup());
  }

  addMaintenanceContact() {
    this.maintenanceContacts.push(this.createContactNumberFormGroup());
  }

  removeSyndicContact(index: number) {
    this.syndicContacts.removeAt(index);
  }

  removeEmergencyContact(index: number) {
    this.emergencyContacts.removeAt(index);
  }

  removeMaintenanceContact(index: number) {
    this.maintenanceContacts.removeAt(index);
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

    // Combine all contact types into a single array with contactType
    const allContacts: UIContactNumber[] = [
      ...formValue.syndicContacts.map((contact: any) => ({ ...contact, contactType: 'syndic' })),
      ...formValue.emergencyContacts.map((contact: any) => ({ ...contact, contactType: 'emergency' })),
      ...formValue.maintenanceContacts.map((contact: any) => ({ ...contact, contactType: 'maintenance' }))
    ];

    const updatedInfos = {
      ...this.infos,
      nextAGDate: this.tuiDayToString(formValue.nextAGDate),
      rulesUrl: formValue.rulesUrl,
      delegates: formValue.delegates,
      contactNumbers: allContacts
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
