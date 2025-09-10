import { Injectable } from "@angular/core";
import { Observable, of } from "rxjs";
import { CreateAnnouncementRequest } from "../../../../api-client/model/createAnnouncementRequest";
import { loadAnnouncementsData } from "../../../../mock/annoncements-loader";
import { UIAnnouncement, UIPaginatedAnnouncementsResponse, UIPaginationParams } from "../../../../models/UIAnnoncements";
import { AnnouncementsService } from "../annoncements.service";

@Injectable({
  providedIn: 'root',
})
export class MockAnnouncementsService implements AnnouncementsService {

  createAnnouncement(announcement: CreateAnnouncementRequest): Observable<UIAnnouncement> {
    // Mock implementation - create a UIAnnouncement from the request
    const mockAnnouncement: UIAnnouncement = {
      id: Math.random().toString(36).substr(2, 9),
      title: announcement.title,
      content: announcement.content,
      category: 'COMMUNITY_EVENT' as any,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
    return of(mockAnnouncement);
  }

  updateAnnouncement(id: string, announcement: UIAnnouncement): Observable<UIAnnouncement> {
    return of(announcement);
  }

  deleteAnnouncement(id: string): Observable<void> {
    return of(undefined);
  }

  getAnnouncements(): Observable<UIAnnouncement[]> {
    return of(loadAnnouncementsData());
  }

  getAnnouncementsPaginated(params: UIPaginationParams): Observable<UIPaginatedAnnouncementsResponse> {
    let announcements = loadAnnouncementsData();

    // Apply sorting by creation date (newest first)
    announcements.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

    // Apply pagination
    const page = params.page || 1;
    const pageSize = params.pageSize || 10;
    const startIndex = (page - 1) * pageSize;
    const endIndex = startIndex + pageSize;
    const paginatedAnnouncements = announcements.slice(startIndex, endIndex);

    const response: UIPaginatedAnnouncementsResponse = {
      totalPages: Math.ceil(announcements.length / pageSize),
      totalItems: announcements.length,
      currentPage: page,
      itemsPerPage: pageSize,
      announcements: paginatedAnnouncements
    };

    return of(response);
  }

  getAnnouncement(id: string): Observable<UIAnnouncement> {
    return of(loadAnnouncementsData().find(announcement => announcement.id === id) as UIAnnouncement);
  }
}