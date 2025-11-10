import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, firstValueFrom, Observable, of } from 'rxjs';
import { AuthApiService } from '../../api-client';
import { UIUser, UIUserType } from '../../models/UIUser';
import { MockUsersService } from '../../portal/admin/services/mock/users.service';
import { AuthService, UserInfo } from '../auth.service';

@Injectable({
  providedIn: 'root',
})
export class MockAuthService implements AuthService {
  private isAuthenticated$ = new BehaviorSubject<boolean>(false);
  private userRoles: string[] = ['admin', 'hub']; // Store multiple roles
  message: string = '';

  private userInfo: UserInfo = {
    firstName: 'demo',
    lastName: 'demo',
    username: 'demo',
    email: 'me@example.com',
    user: {
      id: '1',
      username: 'demo',
      email: 'me@example.com',
      firstName: 'demo',
      lastName: 'demo',
      avatarUrl: 'https://mdbcdn.b-cdn.net/img/new/avatars/2.webp',
      disabled: false,
      isEmailVerified: true,
      streetAddress: '123 Main St',
      city: 'Anytown',
      postalCode: '12345',
      country: 'USA',
      type: UIUserType.ADMIN,
      roles: ['hub', 'admin'],
      preferredLanguage: 'fr',
      createdAt: new Date().toISOString()
    },
  };

  constructor(
    private router: Router,
    private authApiService: AuthApiService,
    private usersService: MockUsersService,
  ) {
    this.userInfo.user = {
      "id": "11",
      "username": "me",
      "firstName": "Me",
      "lastName": "Me",
      "email": "me@example.com",
      "avatarUrl": "https://mdbcdn.b-cdn.net/img/new/avatars/2.webp",
      "isEmailVerified": true,
      "disabled": false,
      "preferredLanguage": "en",
      "flatNumber": "808",
      "streetAddress": "808 Birch St",
      "city": "Somewhere",
      "postalCode": "12345",
      "country": "USA",
      "type": UIUserType.ADMIN,
      "roles": ["hub", "admin"],
      "createdAt": new Date().toISOString()
    };
    this.usersService.getUsers().subscribe((users) => {
      this.userInfo.user = users.find((user) => user.username === 'me') as UIUser;
      if (!this.userInfo.user.avatarUrl) {
        this.userInfo.user.avatarUrl = 'https://mdbcdn.b-cdn.net/img/new/avatars/2.webp';
      }
    });

  }

  async initializeSession(): Promise<void> {
    const userProfile = await firstValueFrom(this.getUserProfile());
    this.userInfo.user = userProfile;
  }

  getUserProfile(): Observable<UIUser> {
    // Update userRoles with the actual user roles
    this.userRoles = this.userInfo.user.roles ?? ['hub', 'admin'];
    return of(this.userInfo.user);
  }

  verifyEmail(token: string): Observable<boolean> {
    return new Observable<boolean>((observer) => {
      observer.next(true);
      observer.complete();
    });
  }

  getCurrentUserInfo(): UserInfo {
    return this.userInfo;
  }

  signIn(username: string, password: string): Observable<boolean> {
    return new Observable<boolean>((observer) => {
      if (username === 'admin' && password === 'password') {
        this.isAuthenticated$.next(true);
        this.userRoles = ['admin', 'hub'];
        this.userInfo = {
          firstName: 'admin',
          lastName: 'admin',
          username: 'admin',
          email: 'admin@example.com',
          user: this.userInfo.user,
        }
        this.usersService.getUsers().subscribe((users) => {
          this.userInfo.user = users.find((user) => user.username === username) as UIUser;
          if (!this.userInfo.user.avatarUrl) {
            this.userInfo.user.avatarUrl = 'https://mdbcdn.b-cdn.net/img/new/avatars/2.webp';
          }
        });

        observer.next(true);
        observer.complete();
      }
      else if (username === 'demo' && password === 'password') {
        this.isAuthenticated$.next(true);
        this.userRoles = ['admin', 'hub'];
        this.userInfo = {
          firstName: 'demo',
          lastName: 'demo',
          username: 'demo',
          email: 'me@example.com',
          user: this.userInfo.user,
        }
        this.usersService.getUsers().subscribe((users) => {
          this.userInfo.user = users.find((user) => user.username === "me") as UIUser;
          if (!this.userInfo.user.avatarUrl) {
            this.userInfo.user.avatarUrl = 'https://mdbcdn.b-cdn.net/img/new/avatars/2.webp';
          }
        });

        observer.next(true);
        observer.complete();
      } else if (username === 'alice' && password === 'password') {
        this.isAuthenticated$.next(true);
        this.userRoles = ['hub'];
        this.userInfo = {
          firstName: 'alice',
          lastName: 'dupont',
          username: 'alice',
          email: 'alice@example.com',
          user: this.userInfo.user,
        }
        this.usersService.getUsers().subscribe((users) => {
          this.userInfo.user = users.find((user) => user.username === username) as UIUser;
          if (!this.userInfo.user.avatarUrl) {
            this.userInfo.user.avatarUrl = 'https://mdbcdn.b-cdn.net/img/new/avatars/2.webp';
          }
        });
        observer.next(true);
        observer.complete();
      } else if (username === 'bob' && password === 'password') {
        this.isAuthenticated$.next(true);
        this.userRoles = ['hub'];
        this.userInfo = {
          firstName: 'bob',
          lastName: 'durand',
          username: 'bob',
          email: 'bob@example.com',
          user: this.userInfo.user,
        }
        this.usersService.getUsers().subscribe((users) => {
          this.userInfo.user = users.find((user) => user.username === username) as UIUser;
          if (!this.userInfo.user.avatarUrl) {
            this.userInfo.user.avatarUrl = 'https://mdbcdn.b-cdn.net/img/new/avatars/2.webp';
          }
        });
        observer.next(true);
        observer.complete();
      }

      else {
        observer.next(false);
        observer.complete();
      }
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
  ): Observable<{ success: boolean; emailAlreadyVerified?: boolean; user?: UIUser; message?: string }> {
    // Mock sign up logic (replace with backend API call)
    console.log('User registered:', { username, firstName, lastName, email, type, password });
    return new Observable<{ success: boolean; emailAlreadyVerified?: boolean; user?: UIUser; message?: string }>((observer) => {
      observer.next({
        success: true,
        emailAlreadyVerified: false,
        message: 'User registered successfully'
      });
      observer.complete();
    });
  }

  signOut(): Observable<boolean> {
    this.isAuthenticated$.next(false);
    this.userRoles = [];
    this.router.navigate(['/']);
    return new Observable<boolean>((observer) => {
      observer.next(true);
      observer.complete();
    });
  }

  isAuthenticated() {
    return this.isAuthenticated$.asObservable();
  }

  resetPassword(email: string): Observable<boolean> {
    console.log(`Password reset email sent to: ${email}`);
    return new Observable<boolean>((observer) => {
      observer.next(true);
      observer.complete();
    });
  }

  confirmResetPassword(token: string, newPassword: string): Observable<boolean> {
    console.log(`Password reset confirmed for token: ${token} with new password: ${newPassword}`);
    return new Observable<boolean>((observer) => {
      observer.next(true);
      observer.complete();
    });
  }

  generateSSOLoginUrl(): Observable<string> {
    return new Observable<string>((observer) => {
      observer.next('https://example.com/sso/login');
      observer.complete();
    });
  }
  exchangeSSOToken(state: string, code: string): Observable<string> {
    return new Observable<string>((observer) => {
      observer.next('success');
      observer.complete();
    });
  }


}
