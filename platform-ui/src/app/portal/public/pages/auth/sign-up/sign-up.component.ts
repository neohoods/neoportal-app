import { NgForOf } from '@angular/common';
import { Component, Inject } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiDataList, TuiHint, TuiIcon, TuiOption, TuiTextfield } from '@taiga-ui/core';
import { TuiDataListWrapper, TuiPassword } from '@taiga-ui/kit';
import { TuiSelectModule } from '@taiga-ui/legacy';
import { WelcomeComponent } from '../../../../../components/welcome/welcome.component';
import {
  AUTH_SERVICE_TOKEN,
  getGlobalProviders,
} from '../../../../../global.provider';
import { UIUserType } from '../../../../../models/UIUser';
import { AuthService } from '../../../../../services/auth.service';

@Component({
  standalone: true,
  selector: 'app-sign-up',
  imports: [
    WelcomeComponent,
    TuiButton,
    TuiTextfield,
    TuiIcon,
    TuiPassword,
    TuiSelectModule,
    TuiDataList,
    TuiDataListWrapper,
    TuiOption,
    TuiHint,
    RouterLink,
    ReactiveFormsModule,
    FormsModule,
    TranslateModule,
    NgForOf
  ],
  templateUrl: './sign-up.component.html',
  styleUrl: './sign-up.component.scss',
  providers: [...getGlobalProviders()],
})
export class SignUpComponent {
  signUpForm: FormGroup;
  // Filter out ADMIN role from signup - only other admins can assign this role
  userTypes = Object.values(UIUserType).filter(type => type !== UIUserType.ADMIN);

  constructor(
    private fb: FormBuilder,
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
    private alerts: TuiAlertService,
    private translate: TranslateService,
    private router: Router,
  ) {
    this.signUpForm = this.fb.group({
      username: ['', Validators.required],
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      type: ['', Validators.required],
      password: ['', [Validators.required, Validators.minLength(6)]],
    });
  }

  stringifyUserType = (userType: UIUserType): string => {
    if (!userType) {
      return "";
    }
    return this.translate.instant(`user.type.${userType}`);
  };

  onSubmit() {
    if (this.signUpForm.valid) {
      const {
        username,
        firstName,
        lastName,
        email,
        type,
        password,
      } = this.signUpForm.value;
      this.authService.signUp(
        username,
        firstName,
        lastName,
        email,
        type,
        password,
      ).subscribe({
        next: (result) => {
          if (result.success) {
            if (result.emailAlreadyVerified) {
              // User already exists with verified email, redirect to success page
              this.router.navigate(['/signup-success'], {
                queryParams: { email: email }
              });
            } else {
              // New user, redirect to email pending page
              this.router.navigate(['/email-pending'], {
                queryParams: { email: email }
              });
            }
          } else {
            this.alerts.open(
              result.message || 'Registration failed! Please try again.',
              { appearance: 'negative' }
            ).subscribe();
          }
        },
        error: () => {
          this.alerts.open(
            'Registration failed! Please try again.',
            { appearance: 'negative' }
          ).subscribe();
        },
      });
    }
  }
}
