import { Component, Inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TuiButton } from '@taiga-ui/core';
import { getGlobalProviders } from '../../../../global.provider';
import { ConfigService, UISettings } from '../../../../services/config.service';

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
export class HomeComponent {
    config: UISettings;

    constructor(@Inject(ConfigService) private configService: ConfigService) {
        this.config = this.configService.getSettings();
    }
}
