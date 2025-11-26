import { Component, Inject, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormsModule,
  ReactiveFormsModule
} from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiLoader, TuiTextfield } from '@taiga-ui/core';
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
    TuiTextfield,
    ReactiveFormsModule,
    FormsModule,
    TranslateModule,
    TuiLoader,
  ],
  templateUrl: './token-exchange.component.html',
  styleUrl: './token-exchange.component.scss',
  providers: [...getGlobalProviders()],
})
export class TokenExchangeComponent implements OnInit {
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
          // Try to restore the original URL from sessionStorage
          const returnUrl = sessionStorage.getItem('sso_return_url');
          if (returnUrl && this.isValidReturnUrl(returnUrl)) {
            // Remove the stored URL to avoid reusing it
            sessionStorage.removeItem('sso_return_url');
            // Redirect to the original URL
            this.router.navigateByUrl(returnUrl);
          } else {
            // Clear invalid or missing return URL
            if (returnUrl) {
              sessionStorage.removeItem('sso_return_url');
            }
            // Fallback to default redirect based on user role
            if (this.authService.hasRole('hub')) {
              this.router.navigate(['/hub']);
            } else {
              this.router.navigate(['/']);
            }
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

  /**
   * Validate that the return URL is safe (internal URL only, no external redirects)
   */
  private isValidReturnUrl(url: string): boolean {
    if (!url || url.trim() === '') {
      return false;
    }
    // Only allow relative URLs (starting with /)
    // Reject external URLs (http://, https://, //, etc.)
    if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('//')) {
      return false;
    }
    // Only allow URLs starting with /
    if (!url.startsWith('/')) {
      return false;
    }
    // Reject certain paths that shouldn't be return URLs
    const forbiddenPaths = ['/login', '/sso/callback', '/sign-up', '/sign-in'];
    if (forbiddenPaths.some(path => url.startsWith(path))) {
      return false;
    }
    return true;
  }
}
