import { Injectable } from "@angular/core";
import { Observable, of } from "rxjs";
import { UINotificationsSettings } from "../../../../models/UINotificationsSettings";
import { UIUser } from "../../../../models/UIUser";
import { ProfileService } from "../profile.service";

@Injectable({
  providedIn: 'root',
})
export class MockProfileService implements ProfileService {
  getNotificationsSettings(): Observable<UINotificationsSettings> {
    return of({ isNotificationsEnabled: true, isNewsletterEnabled: true });
  }
  updateNotificationsSettings(settings: UINotificationsSettings): Observable<UINotificationsSettings> {
    return of(settings);
  }
  updateProfile(user: UIUser): Observable<UIUser> {
    return of(user);
  }
}


