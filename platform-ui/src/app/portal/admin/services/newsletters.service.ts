import { Observable } from 'rxjs';
import {
    CreateNewsletterRequest,
    PaginatedNewslettersResponse,
    ScheduleNewsletterRequest,
    UINewsletter,
    UINewsletterLogEntry,
    UpdateNewsletterRequest
} from '../../../models/UINewsletter';

export interface NewslettersService {
    getNewsletters(page?: number, pageSize?: number): Observable<PaginatedNewslettersResponse>;
    getNewsletter(id: string): Observable<UINewsletter>;
    createNewsletter(request: CreateNewsletterRequest): Observable<UINewsletter>;
    updateNewsletter(id: string, request: UpdateNewsletterRequest): Observable<void>;
    deleteNewsletter(id: string): Observable<void>;
    scheduleNewsletter(id: string, request: ScheduleNewsletterRequest): Observable<void>;
    sendNewsletter(id: string): Observable<void>;
    testNewsletter(id: string): Observable<void>;
    getNewsletterLogs(newsletterId: string, page?: number, pageSize?: number): Observable<UINewsletterLogEntry[]>;
}
