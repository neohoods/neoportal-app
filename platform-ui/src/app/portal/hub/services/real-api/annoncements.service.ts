import { Injectable } from "@angular/core";
import { map, Observable } from "rxjs";
import { AnnouncementsHubApiService } from "../../../../api-client";
import { CreateAnnouncementRequest } from "../../../../api-client/model/createAnnouncementRequest";
import { UIAnnouncement, UIPaginatedAnnouncementsResponse, UIPaginationParams } from "../../../../models/UIAnnoncements";
import { AnnouncementsService } from "../annoncements.service";

@Injectable({
  providedIn: 'root',
})
export class APIAnnouncementsService implements AnnouncementsService {
  constructor(private announcementsApiService: AnnouncementsHubApiService) { }

  createAnnouncement(announcement: CreateAnnouncementRequest): Observable<UIAnnouncement> {
    return this.announcementsApiService.createAnnouncement(announcement);
  }

  updateAnnouncement(id: string, announcement: UIAnnouncement): Observable<UIAnnouncement> {
    return this.announcementsApiService.updateAnnouncement(id, announcement);
  }

  deleteAnnouncement(id: string): Observable<void> {
    return this.announcementsApiService.deleteAnnouncement(id);
  }

  getAnnouncements(): Observable<UIAnnouncement[]> {
    return this.announcementsApiService.getAnnouncements().pipe(
      map(response => response.announcements.map(announcement => ({
        ...announcement,
        category: announcement.category as any
      })))
    );
  }

  getAnnouncementsPaginated(params: UIPaginationParams): Observable<UIPaginatedAnnouncementsResponse> {
    return this.announcementsApiService.getAnnouncements(
      params.page,
      params.pageSize
    ).pipe(
      map(response => ({
        totalPages: response.totalPages,
        totalItems: response.totalItems,
        currentPage: response.currentPage,
        itemsPerPage: response.itemsPerPage,
        announcements: response.announcements.map(announcement => ({
          ...announcement,
          category: announcement.category as any
        }))
      }))
    );
  }

  getAnnouncement(id: string): Observable<UIAnnouncement> {
    return this.announcementsApiService.getAnnouncement(id).pipe(
      map(announcement => ({
        ...announcement,
        category: announcement.category as any
      }))
    );
  }

}