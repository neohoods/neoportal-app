import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { TuiAlertService } from '@taiga-ui/core';
import { BehaviorSubject, firstValueFrom, Observable } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { AuthApiService, ProfileHubApiService, Property, SsoPublicApiService, User } from '../../api-client';
import { UIProperty, UIPropertyType, UIUser, UIUserType } from '../../models/UIUser';
import { AuthService, UserInfo } from '../auth.service';

@Injectable({
  providedIn: 'root',
})
export class APIAuthService implements AuthService {
  private isAuthenticated$ = new BehaviorSubject<boolean>(false);
  private userRoles: string[] = ['admin', 'hub']; // Store multiple roles
  message: string = '';
  userInfo: UserInfo = {
    firstName: 'unknown',
    lastName: 'unknown',
    username: 'unknown',
    email: 'unknown',
    user: {
      id: 'unknown',
      username: 'unknown',
      email: 'unknown',
      avatarUrl: 'unknown',
      disabled: false,
      isEmailVerified: false,
      firstName: 'unknown',
      lastName: 'unknown',
      streetAddress: 'unknown',
      city: 'unknown',
      postalCode: 'unknown',
      country: 'unknown',
      type: UIUserType.EXTERNAL,
      properties: [],
    },
  };

  constructor(
    private router: Router,
    private authApiService: AuthApiService,
    private profileApiService: ProfileHubApiService,
    private ssoPublicApiService: SsoPublicApiService,
    private alerts: TuiAlertService,
  ) { }

  generateSSOLoginUrl(): Observable<string> {
    return this.ssoPublicApiService.generateSSOLoginUrl().pipe(
      map((response) => {
        return response.loginUrl ?? '';
      })
    );
  }

  exchangeSSOToken(state: string, code: string): Observable<string> {
    return this.ssoPublicApiService.exchangeSSOToken({
      state: state,
      authorizationCode: code,
    }).pipe(
      switchMap((response) => {
        // Initialize session after successful token exchange
        return this.initializeSession().then(() => 'success').catch((error) => {
          console.error('Failed to initialize session after SSO token exchange:', error);
          return 'error';
        });
      })
    );
  }


  async initializeSession(): Promise<void> {
    try {
      const userProfile = await firstValueFrom(this.getUserProfile());
      this.userInfo.user = userProfile;
      this.userInfo.firstName = userProfile.firstName;
      this.userInfo.lastName = userProfile.lastName;
      this.userInfo.username = userProfile.username;
      this.userInfo.email = userProfile.email;
      this.userInfo.user = userProfile;
    } catch (error) {
      // Handle failed session restoration (e.g., expired cookie)
      this.signOut();
    }
  }

  getUserProfile(): Observable<UIUser> {
    return this.profileApiService.getProfile().pipe(
      map((user: User) => {
        return {
          ...user,
          type: user.type as UIUserType,
          properties: (user.properties ?? []).map((property: Property) => ({
            ...property,
            type: property.type as UIPropertyType,
          })) as UIProperty[],
        } as UIUser;
      })
    );
  }

  verifyEmail(token: string): Observable<boolean> {
    return this.authApiService.verifyEmail(token).pipe(
      map((response) => {
        return response.success;
      })
    );
  }
  getCurrentUserInfo(): UserInfo {
    return this.userInfo;
  }

  signIn(username: string, password: string): Observable<boolean> {
    return new Observable<boolean>((observer) => {
      this.authApiService
        .login({
          username: username,
          password: password,
        })
        .subscribe({
          next: (user: User) => {
            this.isAuthenticated$.next(true);
            this.userRoles = user.roles ?? []; // Assuming the user object has a roles property
            this.userInfo.firstName = user.firstName;
            this.userInfo.lastName = user.lastName;
            this.userInfo.username = user.username;
            this.userInfo.email = user.email ?? 'unknown';
            this.userInfo.user = {
              id: user.id,
              username: user.username,
              email: user.email ?? 'unknown',
              avatarUrl: user.avatarUrl ?? 'unknown',
              disabled: user.disabled ?? false,
              isEmailVerified: user.isEmailVerified ?? false,
              firstName: user.firstName ?? 'unknown',
              lastName: user.lastName ?? 'unknown',
              streetAddress: user.streetAddress ?? 'unknown',
              city: user.city ?? 'unknown',
              type: user.type as UIUserType,
              properties: (user.properties ?? []).map((property: Property) => ({
                ...property,
                type: property.type as UIPropertyType,
              })) as UIProperty[],
              postalCode: user.postalCode ?? 'unknown',
              country: user.country ?? 'unknown',
            };
            observer.next(true);
            observer.complete();
          },
          error: (error) => {
            observer.next(false);
            observer.complete();
          },
        });
    });
  }

  // Check if the user has a specific role
  hasRole(role: string): boolean {
    return this.userRoles.includes(role);
  }

  signUp(
    username: string,
    firstName: string,
    lastName: string,
    email: string,
    type: UIUserType,
    password: string,
  ): Observable<boolean> {
    // Mock sign up logic (replace with backend API call)
    return new Observable<boolean>((observer) => {
      this.authApiService
        .signUp({
          username: username,
          firstName: firstName,
          lastName: lastName,
          email: email,
          type: type,
          password: password,
        })
        .subscribe({
          next: (response) => {
            observer.next(true);
            observer.complete();
          },
        });
    });
  }

  signOut(): Observable<boolean> {
    return new Observable<boolean>((observer) => {
      this.authApiService.signOut()
        .subscribe({
          next: (response) => {
            observer.next(true);
            observer.complete();
          },
        });
    });
  }

  isAuthenticated() {
    return this.isAuthenticated$.asObservable();
  }

  resetPassword(email: string): Observable<boolean> {
    return new Observable<boolean>(observer => {
      this.authApiService.resetPassword({
        email: email,
      }).subscribe({
        next: () => {
          observer.next(true);
          observer.complete();
        },
        error: () => {
          observer.next(false);
          observer.complete();
        }
      });
    });
  }

  confirmResetPassword(token: string, newPassword: string): Observable<boolean> {
    return new Observable<boolean>(observer => {
      this.authApiService.confirmResetPassword({
        token: token,
        newPassword: newPassword,
      }).subscribe({
        next: () => {
          observer.next(true);
          observer.complete();
        },
        error: () => {
          observer.next(false);
          observer.complete();
        }
      });
    });
  }
}
