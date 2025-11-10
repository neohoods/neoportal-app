import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import {
  GetSecuritySettings200Response,
  SaveSecuritySettingsRequest,
  SettingsAdminApiService,
} from '../../../../api-client';
import { SecuritySettingsService, UISecuritySettings } from '../security-settings.service';


@Injectable({
  providedIn: 'root',
})
export class ApiSecuritySettingsService implements SecuritySettingsService {
  constructor(private settingsAdminApiService: SettingsAdminApiService) { }
  getSecuritySettings(): Observable<UISecuritySettings> {
    return this.settingsAdminApiService.getSecuritySettings().pipe(
      map((response: GetSecuritySettings200Response) => {
        return {
          isRegistrationEnabled: response.isRegistrationEnabled ?? false,
        };
      }),
    );
  }

  saveSecuritySettings(
    settings: UISecuritySettings,
  ): Observable<UISecuritySettings> {
    return this.settingsAdminApiService.saveSecuritySettings(settings).pipe(
      map((response: SaveSecuritySettingsRequest) => {
        return {
          isRegistrationEnabled: response.isRegistrationEnabled ?? false,
        };
      }),
    );
  }
}
