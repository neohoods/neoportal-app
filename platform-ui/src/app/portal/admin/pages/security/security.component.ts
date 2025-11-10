import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiTextfield } from '@taiga-ui/core';
import { TuiSwitch } from '@taiga-ui/kit';
import { SECURITY_SETTINGS_SERVICE_TOKEN } from '../../admin.providers';
import {
  SecuritySettingsService,
  UISecuritySettings,
} from '../../services/security-settings.service';
@Component({
  standalone: true,
  selector: 'app-security',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    TuiButton,
    TuiSwitch,
    TranslateModule,
    CommonModule,
    TuiTextfield,

  ],
  templateUrl: './security.component.html',
  styleUrl: './security.component.scss',
})
export class SecurityComponent implements OnInit {
  securityForm: FormGroup;

  constructor(
    private formBuilder: FormBuilder,
    @Inject(SECURITY_SETTINGS_SERVICE_TOKEN)
    private securitySettingsService: SecuritySettingsService,
    private alerts: TuiAlertService,
    private translate: TranslateService,
  ) {
    this.securityForm = this.formBuilder.group({
      isRegistrationEnabled: [false],
    });
  }

  ngOnInit() {
    this.loadSecuritySettings();
  }

  private loadSecuritySettings() {
    this.securitySettingsService
      .getSecuritySettings()
      .subscribe((settings) => {
        this.securityForm.patchValue(settings);
      });
  }

  onSubmit() {
    if (this.securityForm.valid) {
      const formValue = this.securityForm.value as UISecuritySettings;
      this.securitySettingsService
        .saveSecuritySettings(formValue)
        .subscribe((savedSettings) => {
          // Update form with saved values
          this.securityForm.patchValue(savedSettings);

          // Show success notification
          this.translate.get('security.saveSuccess').subscribe((message: string) => {
            this.alerts
              .open(message, {
                appearance: 'positive',
              })
              .subscribe();
          });
        });
    }
  }

  // Getter for easy template access
  get formControls() {
    return this.securityForm.controls;
  }
}
