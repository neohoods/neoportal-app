import { Injectable } from "@angular/core";
import { map, Observable } from "rxjs";
import { NotificationSettings, NotificationsHubApiService, ProfileHubApiService, User } from "../../../../api-client";
import { UINotificationsSettings } from "../../../../models/UINotificationsSettings";
import { UIUser, UIUserType } from "../../../../models/UIUser";
import { ProfileService } from "../profile.service";

@Injectable({
  providedIn: 'root',
})
export class APIProfileService implements ProfileService {
  constructor(private profileApiService: ProfileHubApiService,
    private notificationsApiService: NotificationsHubApiService
  ) { }

  getNotificationsSettings(): Observable<UINotificationsSettings> {
    return this.notificationsApiService.getNotificationSettings().pipe(
      map((settings: NotificationSettings) => this.mapToUINotificationsSettings(settings))
    );
  }

  updateNotificationsSettings(settings: UINotificationsSettings): Observable<UINotificationsSettings> {
    return this.notificationsApiService.updateNotificationSettings(this.mapToApiNotificationsSettings(settings)).pipe(
      map((settings: NotificationSettings) => this.mapToUINotificationsSettings(settings))
    );
  }
  updateProfile(user: UIUser): Observable<UIUser> {
    return this.profileApiService.updateProfile(user).pipe(
      map((user: User) => {
        return {
          ...user,
          type: user.type as UIUserType,
        } as UIUser;
      })
    );
  }

  private mapToApiNotificationsSettings(settings: UINotificationsSettings): NotificationSettings {
    return {
      enableNotifications: settings.isNotificationsEnabled,
      newsletterEnabled: settings.isNewsletterEnabled,
    };
  }

  private mapToUINotificationsSettings(settings: NotificationSettings): UINotificationsSettings {
    return {
      isNotificationsEnabled: settings.enableNotifications,
      isNewsletterEnabled: settings.newsletterEnabled,
    };
  }
}