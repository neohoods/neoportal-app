import { Observable } from 'rxjs';

export interface UIPublicSettings {
  isRegistrationEnabled: boolean;
  ssoEnabled: boolean;
}

export interface PublicSettingsService {
  getPublicSettings(): Observable<UIPublicSettings>;
}
