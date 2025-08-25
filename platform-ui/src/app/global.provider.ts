// global-providers.ts
import { InjectionToken, Provider } from '@angular/core';
import { AuthService } from './services/auth.service';
import { ConfigService } from './services/config.service';
import { MockAuthService } from './services/mock/auth.service';
import { MockPublicSettingsService } from './services/mock/settings.service';
import { APIAuthService } from './services/real-api/auth.service';
import { APIPublicSettingsService } from './services/real-api/settings.service';
import { PublicSettingsService } from './services/settings.service';

export const AUTH_SERVICE_TOKEN = new InjectionToken<AuthService>(
  'AuthService',
);
export const PUBLIC_SETTINGS_SERVICE_TOKEN =
  new InjectionToken<PublicSettingsService>('PublicSettingsService');

export function getGlobalProviders(): Provider[] {
  return [
    {
      provide: AUTH_SERVICE_TOKEN,
      useExisting: ConfigService.configuration.useMockApi ? MockAuthService : APIAuthService,
    },
    {
      provide: PUBLIC_SETTINGS_SERVICE_TOKEN,
      useExisting: ConfigService.configuration.useMockApi
        ? MockPublicSettingsService
        : APIPublicSettingsService,
    },
  ];
}
