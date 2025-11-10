import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { SecuritySettingsService, UISecuritySettings } from '../security-settings.service';

@Injectable({
  providedIn: 'root',
})
export class MockSecuritySettingsService implements SecuritySettingsService {
  private securitySettings: UISecuritySettings = {
    isRegistrationEnabled: false,
  };

  getSecuritySettings(): Observable<UISecuritySettings> {
    return of(this.securitySettings);
  }
  saveSecuritySettings(
    settings: UISecuritySettings,
  ): Observable<UISecuritySettings> {
    this.securitySettings = settings;
    return of(this.securitySettings);
  }
}
