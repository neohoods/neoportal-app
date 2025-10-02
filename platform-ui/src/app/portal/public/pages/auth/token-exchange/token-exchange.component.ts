import { Component, Inject } from '@angular/core';
import {
  FormBuilder,
  FormsModule,
  ReactiveFormsModule
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
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
    private router: Router,
    private translate: TranslateService
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
        console.log('Token exchange result:', result);
        if (result === 'success') {
          // Check user role and redirect accordingly, same as sign-in flow
          if (this.authService.hasRole('hub')) {
            this.router.navigate(['/hub']);
          } else {
            this.router.navigate(['/']);
          }
        } else {
          // Other errors are handled by the error interceptor
          this.alerts.open(
            'Failed to exchange SSO token',
            { appearance: 'negative' }
          ).subscribe();
        }
      }, (error) => {
        // Errors are handled by the error interceptor
        console.error('Token exchange error:', error);
      });
    } else {
      this.alerts.open(
        'Missing state or authorization code',
        { appearance: 'negative' }
      ).subscribe();
    }
  }
}
