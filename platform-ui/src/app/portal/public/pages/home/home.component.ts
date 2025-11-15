import { Component, Inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TuiButton } from '@taiga-ui/core';
import { getGlobalProviders, PUBLIC_SETTINGS_SERVICE_TOKEN } from '../../../../global.provider';
import { ConfigService, UISettings } from '../../../../services/config.service';
import { PublicSettingsService } from '../../../../services/settings.service';

@Component({
    standalone: true,
    selector: 'app-home',
    imports: [
        RouterLink,
        TranslateModule,
        TuiButton,
    ],
    templateUrl: './home.component.html',
    styleUrl: './home.component.scss',
    providers: [...getGlobalProviders()],
})
export class HomeComponent implements OnInit {
    config: UISettings = { isRegistrationEnabled: false, ssoEnabled: true };
    appConfig = ConfigService.configuration;

    constructor(
        @Inject(ConfigService) private configService: ConfigService,
        @Inject(PUBLIC_SETTINGS_SERVICE_TOKEN) private publicSettingsService: PublicSettingsService
    ) { }

    ngOnInit() {
        // Load configuration settings from API
        this.publicSettingsService.getPublicSettings().subscribe((settings) => {
            this.config = settings;
        });
    }
}
