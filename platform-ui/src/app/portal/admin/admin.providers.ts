import { InjectionToken, Provider } from '@angular/core';
import { ConfigService } from '../../services/config.service';

import { SpaceStatisticsMockService } from '../spaces/services/mock/space-statistics-mock.service';
import { SpaceStatisticsRealService } from '../spaces/services/real-api/space-statistics-real.service';
import { SPACE_STATISTICS_SERVICE_TOKEN } from '../spaces/spaces.provider';
import { ApplicationsService } from './services/applications.service';
import { CustomPagesService } from './services/custom-pages.service';
import { DIGITAL_LOCK_SERVICE_TOKEN } from './services/digital-lock.service';
import { EmailTemplatesService } from './services/email-templates.service';
import { InfosService } from './services/infos.service';
import { MockApplicationsService } from './services/mock/applications.service';
import { MockCustomPagesService } from './services/mock/custom-pages.service';
import { MockAdminDigitalLockService } from './services/mock/digital-lock.service';
import { MockInfosService } from './services/mock/infos.service';
import { MockNewslettersService } from './services/mock/newsletters.service';
import { MockAdminReservationsService } from './services/mock/reservations.service';
import { MockSecuritySettingsService } from './services/mock/security-settings.service';
import { MockSpaceSettingsService } from './services/mock/space-settings.service';
import { MockAdminSpaceStatisticsService } from './services/mock/space-statistics.service';
import { MockAdminSpacesService } from './services/mock/spaces.service';
import { MockUsersService } from './services/mock/users.service';
import { NewslettersService } from './services/newsletters.service';
import { APIApplicationsService } from './services/real-api/applications.service';
import { APICustomPagesService } from './services/real-api/custom-pages.service';
import { RealApiAdminDigitalLockService } from './services/real-api/digital-lock.service';
import { APIInfosService } from './services/real-api/infos.service';
import { ApiNewslettersService } from './services/real-api/newsletters.service';
import { RealApiAdminReservationsService } from './services/real-api/reservations.service';
import { ApiSecuritySettingsService } from './services/real-api/security-settings.service';
import { RealSpaceSettingsService } from './services/real-api/space-settings.service';
import { RealApiAdminSpaceStatisticsService } from './services/real-api/space-statistics.service';
import { RealApiAdminSpacesService } from './services/real-api/spaces.service';
import { ApiUsersService } from './services/real-api/users.service';
import { AdminReservationsService } from './services/reservations.service';
import { SecuritySettingsService } from './services/security-settings.service';
import { SpaceSettingsService } from './services/space-settings.service';
import { AdminSpaceStatisticsService } from './services/space-statistics.service';
import { AdminSpacesService } from './services/spaces.service';
import { UsersService } from './services/users.service';

export type { AdminSpacesService };

export const USERS_SERVICE_TOKEN = new InjectionToken<UsersService>(
  'UsersService',
);
export const SECURITY_SETTINGS_SERVICE_TOKEN =
  new InjectionToken<SecuritySettingsService>('SecuritySettingsService');

export const SPACE_SETTINGS_SERVICE_TOKEN =
  new InjectionToken<SpaceSettingsService>('SpaceSettingsService');

export const CUSTOM_PAGES_SERVICE_TOKEN = new InjectionToken<CustomPagesService>(
  'CustomPagesService',
);
export const INFOS_SERVICE_TOKEN = new InjectionToken<InfosService>(
  'InfosService',
);
export const APPLICATIONS_SERVICE_TOKEN = new InjectionToken<ApplicationsService>(
  'ApplicationsService',
);
export const NEWSLETTERS_SERVICE_TOKEN = new InjectionToken<NewslettersService>(
  'NewslettersService',
);
export const EMAIL_TEMPLATES_SERVICE_TOKEN = new InjectionToken<EmailTemplatesService>(
  'EmailTemplatesService',
);
export const ADMIN_SPACES_SERVICE_TOKEN = new InjectionToken<AdminSpacesService>(
  'AdminSpacesService',
);
export const ADMIN_RESERVATIONS_SERVICE_TOKEN = new InjectionToken<AdminReservationsService>(
  'AdminReservationsService',
);
export const ADMIN_SPACE_STATISTICS_SERVICE_TOKEN = new InjectionToken<AdminSpaceStatisticsService>(
  'AdminSpaceStatisticsService',
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
    provide: SPACE_SETTINGS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockSpaceSettingsService
      : RealSpaceSettingsService,
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
  {
    provide: NEWSLETTERS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockNewslettersService
      : ApiNewslettersService,
  },
  {
    provide: EMAIL_TEMPLATES_SERVICE_TOKEN,
    useExisting: EmailTemplatesService,
  },
  {
    provide: ADMIN_SPACES_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockAdminSpacesService
      : RealApiAdminSpacesService,
  },
  {
    provide: ADMIN_RESERVATIONS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockAdminReservationsService
      : RealApiAdminReservationsService,
  },
  {
    provide: DIGITAL_LOCK_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockAdminDigitalLockService
      : RealApiAdminDigitalLockService,
  },
  {
    provide: SPACE_STATISTICS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? SpaceStatisticsMockService
      : SpaceStatisticsRealService,
  },
  {
    provide: ADMIN_SPACE_STATISTICS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockAdminSpaceStatisticsService
      : RealApiAdminSpaceStatisticsService,
  },
];
