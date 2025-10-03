import { Routes } from '@angular/router';
import { EmailConfirmationComponent } from './pages/auth/email-confirmation/email-confirmation.component';
import { EmailPendingComponent } from './pages/auth/email-pending/email-pending.component';
import { ForgotPasswordComponent } from './pages/auth/forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './pages/auth/reset-password/reset-password.component';
import { SignInComponent } from './pages/auth/sign-in/sign-in.component';
import { SignOutComponent } from './pages/auth/sign-out/sign-out.component';
import { SignUpComponent } from './pages/auth/sign-up/sign-up.component';
import { SignupSuccessComponent } from './pages/auth/signup-success/signup-success.component';
import { TokenExchangeComponent } from './pages/auth/token-exchange/token-exchange.component';
import { HomeComponent } from './pages/home/home.component';
import { PublicLayoutComponent } from './public-layout/public-layout.component';

export const PUBLIC_ROUTES: Routes = [
  {
    path: '',
    component: PublicLayoutComponent,
    children: [
      { path: '', component: HomeComponent }, // Home page as default
      { path: 'login', component: SignInComponent },
      { path: 'sign-out', component: SignOutComponent },
      { path: 'sign-up', component: SignUpComponent },
      { path: 'signup-success', component: SignupSuccessComponent },
      { path: 'email-pending', component: EmailPendingComponent },
      { path: 'forgot-password', component: ForgotPasswordComponent },
      { path: 'reset-password', component: ResetPasswordComponent },
      { path: 'email-confirmation', component: EmailConfirmationComponent },
      { path: 'token-exchange', component: TokenExchangeComponent },
    ],
  },
];
