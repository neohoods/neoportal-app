import { Observable } from 'rxjs';
import { CreateAnnouncementRequest } from '../../../api-client/model/createAnnouncementRequest';
import { UIAnnouncement } from '../../../models/UIAnnoncements';



export interface AnnouncementsService {
  getAnnouncements(): Observable<UIAnnouncement[]>;
  getAnnouncement(id: string): Observable<UIAnnouncement>;
  createAnnouncement(announcement: CreateAnnouncementRequest): Observable<UIAnnouncement>;
  updateAnnouncement(id: string, announcement: UIAnnouncement): Observable<UIAnnouncement>;
  deleteAnnouncement(id: string): Observable<void>;
}
