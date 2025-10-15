import { InjectionToken, Provider } from '@angular/core';
import { ConfigService } from '../../services/config.service';
import { MockReservationsService } from './services/mock/reservations.service';
import { SpaceStatisticsMockService } from './services/mock/space-statistics-mock.service';
import { MockSpacesService } from './services/mock/spaces.service';
import { APIReservationsService } from './services/real-api/reservations.service';
import { SpaceStatisticsRealService } from './services/real-api/space-statistics-real.service';
import { APISpacesService } from './services/real-api/spaces.service';
import { ReservationsService } from './services/reservations.service';
import { SpaceStatisticsService } from './services/space-statistics.service';
import { SpacesService } from './services/spaces.service';

export const SPACES_SERVICE_TOKEN = new InjectionToken<SpacesService>(
  'SpacesService',
);

export const RESERVATIONS_SERVICE_TOKEN = new InjectionToken<ReservationsService>(
  'ReservationsService',
);

export const SPACE_STATISTICS_SERVICE_TOKEN = new InjectionToken<SpaceStatisticsService>(
  'SpaceStatisticsService',
);

export const spacesProviders: Provider[] = [
  {
    provide: SPACES_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockSpacesService
      : APISpacesService,
  },
  {
    provide: RESERVATIONS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? MockReservationsService
      : APIReservationsService,
  },
  {
    provide: SPACE_STATISTICS_SERVICE_TOKEN,
    useExisting: ConfigService.configuration.useMockApi
      ? SpaceStatisticsMockService
      : SpaceStatisticsRealService,
  },
];



