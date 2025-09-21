import { Component, Inject, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiTextfield } from '@taiga-ui/core';
import { TuiPassword } from '@taiga-ui/kit';
import { WelcomeComponent } from '../../../../../components/welcome/welcome.component';
import {
  AUTH_SERVICE_TOKEN,
  getGlobalProviders,
} from '../../../../../global.provider';
import { AuthService } from '../../../../../services/auth.service';
import { ConfigService, UISettings } from '../../../../../services/config.service';

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
  ],
  templateUrl: './sign-in.component.html',
  styleUrl: './sign-in.component.scss',
  providers: [...getGlobalProviders()],
})
export class SignInComponent implements OnInit {
  signInForm: FormGroup;
  config: UISettings;
  appConfig = ConfigService.configuration;

  constructor(
    private fb: FormBuilder,
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
    private configService: ConfigService,
    private router: Router,
    private route: ActivatedRoute,
    private alerts: TuiAlertService,
  ) {
    this.signInForm = this.fb.group({
      username: ['', [Validators.required]],
      password: ['', Validators.required],
    });
    this.config = this.configService.getSettings();
    if (ConfigService.configuration.demoMode) {
      this.signInForm.patchValue({
        username: 'demo',
        password: 'password',
      });
    }
  }

  ngOnInit() {
    // Check if user just verified their email
    this.route.queryParams.subscribe(params => {
      if (params['verified'] === 'true' && params['message'] === 'email-verified') {
        this.alerts.open(
          'Email vérifié avec succès ! Vous pouvez maintenant vous connecter.',
          { appearance: 'positive' }
        ).subscribe();
      }
    });

    // Auto-redirect to SSO if enabled
    if (this.config.ssoEnabled) {
      this.loginWithSSO();
    }
  }

  onSubmit() {
    if (this.signInForm.valid) {
      const { username, password } = this.signInForm.value;
      this.authService
        .signIn(username, password)
        .subscribe((isAuthenticated: boolean) => {
          if (isAuthenticated) {
            if (this.authService.hasRole('hub')) {
              this.router.navigate(['/hub']);
            } else {
              this.router.navigate(['/']);
            }
          } else {
            this.alerts.open(
              'Invalid credentials',
              { appearance: 'negative' }
            ).subscribe();
            return;
          }
        });
    }
  }

  loginWithSSO() {
    this.authService.generateSSOLoginUrl().subscribe((url: string) => {
      if (url) {
        // Add a 2-second delay to show the redirecting message
        setTimeout(() => {
          window.location.href = url;
        }, 2000);
      } else {
        this.alerts.open(
          'Failed to generate SSO login URL',
          { appearance: 'negative' }
        ).subscribe();
      }
    });
  }
}
