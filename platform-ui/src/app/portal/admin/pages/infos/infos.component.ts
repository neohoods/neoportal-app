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
      syndic: [],
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
        syndic: this.fb.array([]),
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
        // Normaliser les données pour gérer la compatibilité avec l'ancienne structure
        const normalizedInfos = this.normalizeInfosData(infos);
        this.infos = normalizedInfos;
        this.editInfosForm.patchValue({
          nextAGDate: normalizedInfos.nextAGDate ? this.stringToTuiDay(normalizedInfos.nextAGDate) : null,
          rulesUrl: normalizedInfos.rulesUrl
        });
        this.setDelegates(normalizedInfos.delegates);
        this.setContactNumbers(normalizedInfos.contactNumbers);
      });
  }

  /**
   * Normalise les données pour gérer la compatibilité avec l'ancienne structure
   * où syndic était un objet unique au lieu d'un tableau
   */
  private normalizeInfosData(infos: any): UIInfo {
    const normalized = { ...infos };

    // Si syndic est un objet unique, le convertir en tableau
    if (normalized.contactNumbers?.syndic && !Array.isArray(normalized.contactNumbers.syndic)) {
      normalized.contactNumbers.syndic = [normalized.contactNumbers.syndic];
    }

    // S'assurer que syndic est un tableau
    if (!normalized.contactNumbers?.syndic) {
      normalized.contactNumbers = {
        ...normalized.contactNumbers,
        syndic: []
      };
    }

    return normalized;
  }

  get delegates(): FormArray {
    return this.editInfosForm.get('delegates') as FormArray;
  }

  get emergencyContacts(): FormArray {
    return this.editInfosForm.get('contactNumbers.emergency') as FormArray;
  }

  get syndicContacts(): FormArray {
    return this.editInfosForm.get('contactNumbers.syndic') as FormArray;
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
    // Set syndic contacts
    const syndicArray = this.syndicContacts;
    syndicArray.clear();
    if (contactNumbers.syndic && contactNumbers.syndic.length > 0) {
      contactNumbers.syndic.forEach((syndic: any) => {
        const syndicGroup = this.createContactNumberFormGroup();
        syndicGroup.patchValue(syndic);
        syndicArray.push(syndicGroup);
      });
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

  addSyndicContact() {
    this.syndicContacts.push(this.createContactNumberFormGroup());
  }

  removeSyndicContact(index: number) {
    this.syndicContacts.removeAt(index);
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

    // Normaliser les données avant l'envoi pour s'assurer que syndic est un tableau
    const normalizedInfos = this.normalizeInfosData(updatedInfos);

    this.infosService
      .updateInfos(normalizedInfos)
      .subscribe(() => {
        this.alerts
          .open(this.translate.instant('infos.updateSuccess') || 'Community information updated successfully', {
            appearance: 'positive',
          })
          .subscribe();
      });
  }
}
