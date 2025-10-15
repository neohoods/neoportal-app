import { InjectionToken, Provider } from '@angular/core';
import { ConfigService } from '../../services/config.service';
import { AnnouncementsService } from './services/annoncements.service';
import { ApplicationsService } from './services/applications.service';
import { CustomPageService } from './services/custom-page.service';
import { HelpService } from './services/help.service';
import { InfosService } from './services/infos.service';
import { MockAnnouncementsService } from './services/mock/annoncements.service';
import { MockApplicationsService } from './services/mock/applications.service';
import { MockCustomPageService } from './services/mock/custom-pages.service';
import { MockHelpService } from './services/mock/help.service';
import { MockInfosService } from './services/mock/infos.service';
import { MockProfileService } from './services/mock/profile.service';
import { ProfileService } from './services/profile.service';
import { APIAnnouncementsService } from './services/real-api/annoncements.service';
import { APIApplicationsService } from './services/real-api/applications.service';
import { APICustomPageService } from './services/real-api/custom-pages.service';
import { APIHelpService } from './services/real-api/help.service';
import { APIInfosService } from './services/real-api/infos.service';
import { APIProfileService } from './services/real-api/profile.service';


export const ANNOUNCEMENTS_SERVICE_TOKEN = new InjectionToken<AnnouncementsService>(
  'AnnouncementsService',
);
export const INFOS_SERVICE_TOKEN = new InjectionToken<InfosService>(
  'InfosService',
);
export const HELP_SERVICE_TOKEN = new InjectionToken<HelpService>(
  'HelpService',
);
export const CUSTOM_PAGE_SERVICE_TOKEN = new InjectionToken<CustomPageService>(
  'CustomPageService',
);
export const APPLICATIONS_SERVICE_TOKEN = new InjectionToken<ApplicationsService>(
  'ApplicationsService',
);
export const PROFILE_SERVICE_TOKEN = new InjectionToken<ProfileService>(
  'ProfileService',
);

export const hubProviders: Provider[] = [
  {
    provide: ANNOUNCEMENTS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi ? MockAnnouncementsService : APIAnnouncementsService,
  },
  {
    provide: INFOS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi ? MockInfosService : APIInfosService,
  },
  {
    provide: HELP_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockHelpService
      : APIHelpService,
  },
  {
    provide: CUSTOM_PAGE_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockCustomPageService
      : APICustomPageService,
  },
  {
    provide: APPLICATIONS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockApplicationsService
      : APIApplicationsService,
  },
  {
    provide: PROFILE_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockProfileService
      : APIProfileService,
  },
];
