import { Inject, Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AUTH_SERVICE_TOKEN } from '../global.provider';
import { AuthService } from '../services/auth.service';
import { UIUser } from '../models/UIUser';

@Injectable({
  providedIn: 'root',
})
export class ProfileCompletionGuard implements CanActivate {
  constructor(
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
    private router: Router,
  ) {}

  canActivate(): boolean {
    if (!this.authService.isAuthenticated()) {
      return true; // Let other guards handle authentication
    }

    const user = this.authService.getCurrentUserInfo()?.user;
    if (!user) {
      return true; // Let other guards handle this
    }

    // Check if profile is complete
    const isComplete = this.isProfileComplete(user);

    // If profile is incomplete and not already on profile page, redirect
    if (!isComplete) {
      const currentUrl = this.router.url;
      if (!currentUrl.includes('/hub/settings/profile')) {
        this.router.navigate(['/hub/settings/profile']);
        return false;
      }
    }

    return true;
  }

  private isProfileComplete(user: UIUser): boolean {
    // Check required fields
    if (!user.firstName?.trim()) return false;
    if (!user.lastName?.trim()) return false;
    if (!user.email?.trim()) return false;
    if (!user.type) return false;

    // Check address fields
    if (!user.streetAddress?.trim()) return false;
    if (!user.city?.trim()) return false;
    if (!user.postalCode?.trim()) return false;
    if (!user.country?.trim()) return false;

    return true;
  }
}

