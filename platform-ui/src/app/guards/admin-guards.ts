import { Inject, Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AUTH_SERVICE_TOKEN } from '../global.provider';
import { AuthService } from '../services/auth.service';

@Injectable({
  providedIn: 'root',
})
export class AdminGuard implements CanActivate {
  constructor(
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
    private router: Router,
  ) {
    console.log('🔧 AdminGuard constructor called');
  }

  canActivate(): boolean {
    console.log('🔒 AdminGuard called');
    console.log('🔍 isAuthenticated:', this.authService.isAuthenticated());
    console.log('🔍 hasRole admin:', this.authService.hasRole('admin'));
    if (
      this.authService.isAuthenticated() &&
      this.authService.hasRole('admin')
    ) {
      console.log('✅ AdminGuard: Access granted');
      return true;
    }
    console.log('❌ AdminGuard: Access denied, redirecting to home');
    this.router.navigate(['/hub']); // Redirect to the home page if unauthorized
    return false;
  }
}
