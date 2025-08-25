import { InjectionToken, Provider } from '@angular/core';
import { ConfigService } from '../../services/config.service';

import { MockSecuritySettingsService } from './services/mock/security-settings.service';
import { MockUsersService } from './services/mock/users.service';

import { ApiSecuritySettingsService } from './services/real-api/security-settings.service';
import { ApiUsersService } from './services/real-api/users.service';
import { SecuritySettingsService } from './services/security-settings.service';
import { UsersService } from './services/users.service';

export const USERS_SERVICE_TOKEN = new InjectionToken<UsersService>(
  'UsersService',
);
export const SECURITY_SETTINGS_SERVICE_TOKEN =
  new InjectionToken<SecuritySettingsService>('SecuritySettingsService');

export const adminProviders: Provider[] = [
  
  {
    provide: USERS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi ? MockUsersService : ApiUsersService,
  },
  {
    provide: SECURITY_SETTINGS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockSecuritySettingsService
      : ApiSecuritySettingsService,
  }
];
