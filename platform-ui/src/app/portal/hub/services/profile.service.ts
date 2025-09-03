import { Observable } from 'rxjs';
import { UIUser } from '../../../models/UIUser';
import { UINotificationsSettings } from '../../../models/UINotificationsSettings';

export interface ProfileService {
  updateProfile(user: UIUser): Observable<UIUser>;
  getNotificationsSettings(): Observable<UINotificationsSettings>;
  updateNotificationsSettings(settings: UINotificationsSettings): Observable<UINotificationsSettings>;
}
