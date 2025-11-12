import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { UserBasic, UsersHubApiService } from '../../../../api-client';
import { UIUser } from '../../../../models/UIUser';
import { UsersService } from '../../../admin/services/users.service';

@Injectable({
  providedIn: 'root',
})
export class APIHubUsersService implements UsersService {
  constructor(private usersHubApiService: UsersHubApiService) { }

  getUsers(): Observable<UIUser[]> {
    return this.usersHubApiService.getHubUsers().pipe(
      map((users: UserBasic[]) => users.map((user: UserBasic) => this.mapToUIUser(user)))
    );
  }

  getUser(id: string): Observable<UIUser> {
    // Not implemented in hub API - return empty observable or throw error
    throw new Error('getUser not available in hub API');
  }

  getUsersByIds(userIds: string[]): Observable<UIUser[]> {
    // Not implemented in hub API - return empty observable or throw error
    throw new Error('getUsersByIds not available in hub API');
  }

  saveUser(user: UIUser): Observable<UIUser> {
    // Not implemented in hub API - return empty observable or throw error
    throw new Error('saveUser not available in hub API');
  }

  setUserPassword(userId: string, newPassword: string): Observable<void> {
    // Not implemented in hub API - return empty observable or throw error
    throw new Error('setUserPassword not available in hub API');
  }

  deleteUser(id: string): Observable<void> {
    // Not implemented in hub API - return empty observable or throw error
    throw new Error('deleteUser not available in hub API');
  }

  private mapToUIUser(user: UserBasic): UIUser {
    return {
      id: user.id,
      email: user.email || '',
      flatNumber: '',
      streetAddress: '',
      city: '',
      postalCode: '',
      country: '',
      username: '',
      firstName: user.firstName || '',
      lastName: user.lastName || '',
      isEmailVerified: false,
      disabled: false,
      avatarUrl: '',
      preferredLanguage: '',
      type: undefined as any,
      roles: [],
      createdAt: '',
    };
  }
}

