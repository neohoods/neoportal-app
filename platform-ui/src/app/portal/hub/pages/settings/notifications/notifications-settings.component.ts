import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule
} from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiTextfield } from '@taiga-ui/core';
import { TuiSwitch } from '@taiga-ui/kit';
import { AUTH_SERVICE_TOKEN } from '../../../../../global.provider';
import { AuthService } from '../../../../../services/auth.service';
import { PROFILE_SERVICE_TOKEN } from '../../../hub.provider';
import { ProfileService } from '../../../services/profile.service';

@Component({
  selector: 'app-notifications-settings',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TuiButton,
    TuiTextfield,
    TranslateModule,
    TuiSwitch
  ],
  templateUrl: './notifications-settings.component.html',
  styleUrl: './notifications-settings.component.scss',
})
export class NotificationsSettingsComponent {
  notificationsForm: FormGroup;
  calendarUrl: string | null = null;

  constructor(
    private fb: FormBuilder,
    private alerts: TuiAlertService,
    private translate: TranslateService,
    @Inject(PROFILE_SERVICE_TOKEN) private profileService: ProfileService,
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
  ) {
    this.notificationsForm = this.fb.group({
      isNotificationsEnabled: [true],
      isNewsletterEnabled: [true],
    });
  }

  ngOnInit(): void {
    this.loadProfile();
  }

  // Load initial profile data
  loadProfile(): void {
    this.profileService.getNotificationsSettings().subscribe((settings) => {
      this.notificationsForm.patchValue(settings);
      this.calendarUrl = settings.calendarUrl || null;
    });
  }

  copyCalendarUrl(): void {
    if (!this.calendarUrl) return;
    
    navigator.clipboard.writeText(this.calendarUrl).then(() => {
      this.alerts.open(this.translate.instant('notifications.calendar.urlCopied'), {
        appearance: 'success',
      }).subscribe();
    }).catch((err) => {
      console.error('Failed to copy URL:', err);
      this.alerts.open(this.translate.instant('notifications.calendar.errorCopying') || 'Failed to copy URL', {
        appearance: 'error',
      }).subscribe();
    });
  }

  onSave(): void {
    if (this.notificationsForm.valid) {
      this.profileService
        .updateNotificationsSettings(this.notificationsForm.value)
        .subscribe((settings) => {
          this.alerts.open(this.translate.instant('notifications.saveSuccess'), {
            appearance: 'success',
          }).subscribe();
        });
    } else {
      this.alerts.open(this.translate.instant('notifications.saveError'), {
        appearance: 'error',
      }).subscribe();
    }
  }
}
