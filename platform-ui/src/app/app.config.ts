import {
  APP_INITIALIZER,
  ApplicationConfig,
  importProvidersFrom,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import {
  provideRouter,
  withComponentInputBinding,
  withRouterConfig,
} from '@angular/router';
import { NG_EVENT_PLUGINS } from '@taiga-ui/event-plugins';

import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { TranslateLoader, TranslateModule, TranslateService } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { TUI_LANGUAGE } from '@taiga-ui/i18n/tokens';
import { TUI_FRENCH_LANGUAGE, TUI_ENGLISH_LANGUAGE } from '@taiga-ui/i18n/languages';
import { type TuiLanguage } from '@taiga-ui/i18n/types';
import { BehaviorSubject } from 'rxjs';
import { BASE_PATH, Configuration } from './api-client';
import { routes } from './app.routes';
import { AUTH_SERVICE_TOKEN, getGlobalProviders } from './global.provider';
import { errorInterceptor } from './interceptors/error.interceptor';
import { xhrInterceptor } from './interceptors/xhr.interceptor';
import { AuthService } from './services/auth.service';
import { ConfigService } from './services/config.service';

const httpLoaderFactory: (http: HttpClient) => TranslateHttpLoader = (http: HttpClient) =>
  new TranslateHttpLoader(http, './i18n/', '.json');

export const appConfig: ApplicationConfig = {
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: () => async () => {
        console.log('APP_INITIALIZER: Starting ConfigService initialization...');
        await ConfigService.initialize();
        console.log('APP_INITIALIZER: ConfigService initialization complete');
      },
      multi: true,
      deps: []
    },
    {
      provide: Configuration,
      useValue: new Configuration({
        withCredentials: true
      })
    },
    {
      provide: BASE_PATH,
      useFactory: () => {
        console.log('BASE_PATH: Getting API path from config:', ConfigService.configuration.API_BASE_PATH);
        return ConfigService.configuration.API_BASE_PATH;
      },
      deps: []
    },
    ...getGlobalProviders(),
    {
      provide: APP_INITIALIZER,
      useFactory: (initializer: AuthService) => () => initializer.initializeSession(),
      deps: [AUTH_SERVICE_TOKEN],  // Use the injection token instead of the interface
      multi: true
    },
    provideAnimations(),
    provideHttpClient(
      withInterceptors([errorInterceptor, xhrInterceptor])
    ),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withRouterConfig({
        paramsInheritanceStrategy: 'always',
      }),
    ),
    importProvidersFrom([TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: httpLoaderFactory,
        deps: [HttpClient],
      },
    })]),
    {
      provide: TUI_LANGUAGE,
      useFactory: (translate: TranslateService) => {
        // Get initial language from ConfigService or TranslateService
        const initialLang = translate.currentLang || translate.defaultLang || ConfigService.configuration?.defaultLocale || 'fr';
        const initialLanguage = initialLang === 'fr' ? TUI_FRENCH_LANGUAGE : TUI_ENGLISH_LANGUAGE;
        
        // Create a BehaviorSubject that will emit the current language
        const languageSubject = new BehaviorSubject<TuiLanguage>(initialLanguage);

        // Listen to language changes and update the subject
        translate.onLangChange.subscribe((event) => {
          const language = event.lang === 'fr' ? TUI_FRENCH_LANGUAGE : TUI_ENGLISH_LANGUAGE;
          languageSubject.next(language);
        });

        // Also listen to default language changes
        translate.onDefaultLangChange.subscribe((event) => {
          const language = event.lang === 'fr' ? TUI_FRENCH_LANGUAGE : TUI_ENGLISH_LANGUAGE;
          languageSubject.next(language);
        });

        return languageSubject.asObservable();
      },
      deps: [TranslateService],
    },
    NG_EVENT_PLUGINS,
    provideAnimationsAsync()
  ],
};
