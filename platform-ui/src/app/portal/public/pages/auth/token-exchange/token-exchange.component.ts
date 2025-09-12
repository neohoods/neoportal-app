import { Component, Inject } from '@angular/core';
import {
  FormBuilder,
  FormsModule,
  ReactiveFormsModule
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiLoader, TuiTextfield } from '@taiga-ui/core';
import { TuiPassword } from '@taiga-ui/kit';
import { WelcomeComponent } from '../../../../../components/welcome/welcome.component';
import {
  AUTH_SERVICE_TOKEN,
  getGlobalProviders,
} from '../../../../../global.provider';
import { AuthService } from '../../../../../services/auth.service';
import {
  ConfigService,
  UISettings,
} from '../../../../../services/config.service';

@Component({
  standalone: true,
  selector: 'app-sign-in',
  imports: [
    WelcomeComponent,
    TuiButton,
    TuiTextfield,
    TuiIcon,
    TuiPassword,
    RouterLink,
    ReactiveFormsModule,
    FormsModule,
    TranslateModule,
    TuiLoader,
  ],
  templateUrl: './token-exchange.component.html',
  styleUrl: './token-exchange.component.scss',
  providers: [...getGlobalProviders()],
})
export class TokenExchangeComponent {
  config: UISettings;

  constructor(
    private fb: FormBuilder,
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
    private configService: ConfigService,
    private alerts: TuiAlertService,
    private router: Router
  ) {
    this.config = this.configService.getSettings();
  }

  ngOnInit() {
    const urlParams = new URLSearchParams(window.location.search);
    const fragmentParams = new URLSearchParams(window.location.hash.substring(1));

    const state = urlParams.get('state') || fragmentParams.get('state');
    const code = urlParams.get('code') || fragmentParams.get('code');

    if (state && code) {
      this.authService.exchangeSSOToken(state, code).subscribe((result: string) => {
        if (result === 'success') {
          // Check user role and redirect accordingly, same as sign-in flow
          if (this.authService.hasRole('hub')) {
            this.router.navigate(['/hub']);
          } else {
            this.router.navigate(['/']);
          }
        } else {
          this.alerts.open(
            'Failed to exchange SSO token',
            { appearance: 'negative' }
          ).subscribe();
        }
      }, (error) => {
        this.alerts.open(
          'An error occurred during SSO token exchange',
          { appearance: 'negative' }
        ).subscribe();
      });
    } else {
      this.alerts.open(
        'Missing state or authorization code',
        { appearance: 'negative' }
      ).subscribe();
    }
  }
}
