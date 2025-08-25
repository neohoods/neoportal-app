import { Observable } from 'rxjs';

export interface UISecuritySettings {
  isRegistrationEnabled: boolean;
  ssoEnabled: boolean;
  ssoClientId: string;
  ssoClientSecret: string;
  ssoTokenEndpoint: string;
  ssoAuthorizationEndpoint: string;
  ssoScope: string;
}

export interface SecuritySettingsService {
  getSecuritySettings(): Observable<UISecuritySettings>;
  saveSecuritySettings(
    settings: UISecuritySettings,
  ): Observable<UISecuritySettings>;
}
