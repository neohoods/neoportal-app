import { InjectionToken, Provider } from '@angular/core';
import { ConfigService } from '../../services/config.service';

import { MockSecuritySettingsService } from './services/mock/security-settings.service';
import { MockUsersService } from './services/mock/users.service';

import { CustomPagesService } from './services/custom-pages.service';
import { InfosService } from './services/infos.service';
import { MockCustomPagesService } from './services/mock/custom-pages.service';
import { MockInfosService } from './services/mock/infos.service';
import { APICustomPagesService } from './services/real-api/custom-pages.service';
import { APIInfosService } from './services/real-api/infos.service';
import { ApiSecuritySettingsService } from './services/real-api/security-settings.service';
import { ApiUsersService } from './services/real-api/users.service';
import { SecuritySettingsService } from './services/security-settings.service';
import { UsersService } from './services/users.service';
import { ApplicationsService } from './services/applications.service';
import { MockApplicationsService } from './services/mock/applications.service';
import { APIApplicationsService } from './services/real-api/applications.service';

export const USERS_SERVICE_TOKEN = new InjectionToken<UsersService>(
  'UsersService',
);
export const SECURITY_SETTINGS_SERVICE_TOKEN =
  new InjectionToken<SecuritySettingsService>('SecuritySettingsService');

export const CUSTOM_PAGES_SERVICE_TOKEN = new InjectionToken<CustomPagesService>(
  'CustomPagesService',
);
export const INFOS_SERVICE_TOKEN = new InjectionToken<InfosService>(
  'InfosService',
);
export const APPLICATIONS_SERVICE_TOKEN = new InjectionToken<ApplicationsService>(
  'ApplicationsService',
);

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
  },
  {
    provide: CUSTOM_PAGES_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockCustomPagesService
      : APICustomPagesService,
  },
  {
    provide: INFOS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockInfosService
      : APIInfosService,
  },
  {
    provide: APPLICATIONS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockApplicationsService
      : APIApplicationsService,
  },
];
