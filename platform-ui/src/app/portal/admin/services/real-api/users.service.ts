import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Property, PropertyType, User, UserType } from '../../../../api-client';
import { UsersAdminApiService } from '../../../../api-client/api/usersAdminApi.service';
import { UIProperty, UIPropertyType, UIUser, UIUserType } from '../../../../models/UIUser';
import { UsersService } from '../users.service';

@Injectable({
  providedIn: 'root',
})
export class ApiUsersService implements UsersService {
  constructor(private usersAdminApiService: UsersAdminApiService) { }

  getUsers(): Observable<UIUser[]> {
    return this.usersAdminApiService.getUsers().pipe(
      map((users: User[]) => users.map((user: User) => this.mapToUIUser(user)))
    );
  }

  getUser(id: string): Observable<UIUser> {
    return this.usersAdminApiService.getUser(id).pipe(
      map((user: User) => this.mapToUIUser(user))
    );
  }

  saveUser(user: UIUser): Observable<UIUser> {
    return this.usersAdminApiService.saveUser(this.mapToApiUser(user)).pipe(
      map((savedUser: User) => this.mapToUIUser(savedUser))
    );
  }

  setUserPassword(userId: string, newPassword: string): Observable<void> {
    return this.usersAdminApiService.setUserPassword(userId, {
      newPassword: newPassword,
    });
  }

  private mapToApiUser(uiUser: UIUser): User {
    return {
      id: uiUser.id,
      email: uiUser.email,
      flatNumber: uiUser.flatNumber,
      streetAddress: uiUser.streetAddress,
      city: uiUser.city,
      postalCode: uiUser.postalCode,
      country: uiUser.country,
      username: uiUser.username,
      firstName: uiUser.firstName,
      lastName: uiUser.lastName,
      isEmailVerified: uiUser.isEmailVerified,
      disabled: uiUser.disabled,
      avatarUrl: uiUser.avatarUrl,
      preferredLanguage: uiUser.preferredLanguage,
      roles: [],
      type: uiUser.type as UserType,
      properties: (uiUser.properties ?? []).map((property: UIProperty) => ({
        name: property.name,
        type: property.type as PropertyType,
      })) as Property[],
    };
  }

  private mapToUIUser(user: User): UIUser {
    return {
      id: user.id,
      email: user.email || '',
      flatNumber: user.flatNumber || '',
      streetAddress: user.streetAddress || '',
      city: user.city || '',
      postalCode: user.postalCode || '',
      country: user.country || '',
      username: user.username || '',
      firstName: user.firstName || '',
      lastName: user.lastName || '',
      isEmailVerified: user.isEmailVerified || false,
      disabled: user.disabled || false,
      avatarUrl: user.avatarUrl || '',
      preferredLanguage: user.preferredLanguage || '',
      type: user.type as UIUserType,
      properties: (user.properties ?? []).map((property: Property) => ({
        name: property.name,
        type: property.type as UIPropertyType,
      })) as UIProperty[],
    };
  }
}
