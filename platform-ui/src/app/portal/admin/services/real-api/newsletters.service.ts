import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
    NewsletterRequest as ApiNewsletterRequest,
    PaginatedNewslettersResponse as ApiPaginatedNewslettersResponse,
    SendNewsletterRequest as ApiSendNewsletterRequest,
    Newsletter,
    NewsletterAudience,
    NewsletterLogEntry,
    NewsletterLogsResponse,
    UserType
} from '../../../../api-client';
import { NewslettersAdminApiService } from '../../../../api-client/api/newslettersAdminApi.service';
import {
    CreateNewsletterRequest,
    NewsletterStatus,
    PaginatedNewslettersResponse,
    ScheduleNewsletterRequest,
    UINewsletter,
    UINewsletterAudience,
    UINewsletterLogEntry,
    UpdateNewsletterRequest
} from '../../../../models/UINewsletter';
import { NewslettersService } from '../newsletters.service';

@Injectable({
    providedIn: 'root',
})
export class ApiNewslettersService implements NewslettersService {
    constructor(private newslettersAdminApiService: NewslettersAdminApiService) { }

    getNewsletters(page: number = 1, pageSize: number = 10): Observable<PaginatedNewslettersResponse> {
        return this.newslettersAdminApiService.getNewsletters(page, pageSize).pipe(
            map((response: ApiPaginatedNewslettersResponse) => ({
                totalPages: response.totalPages || 0,
                totalItems: response.totalItems || 0,
                currentPage: response.currentPage || 1,
                itemsPerPage: response.itemsPerPage || 10,
                newsletters: (response.newsletters || []).map((newsletter: Newsletter) => this.mapToUINewsletter(newsletter))
            }))
        );
    }

    getNewsletter(id: string): Observable<UINewsletter> {
        return this.newslettersAdminApiService.getNewsletter(id).pipe(
            map((newsletter: Newsletter) => this.mapToUINewsletter(newsletter))
        );
    }

    createNewsletter(request: CreateNewsletterRequest): Observable<UINewsletter> {
        const apiRequest: ApiNewsletterRequest = {
            subject: request.subject,
            content: request.content,
            audience: this.mapToApiAudience(request.audience),
            scheduledAt: request.scheduledAt || undefined
        };
        return this.newslettersAdminApiService.createNewsletter(apiRequest).pipe(
            map((newsletter: Newsletter) => this.mapToUINewsletter(newsletter))
        );
    }

    updateNewsletter(id: string, request: UpdateNewsletterRequest): Observable<void> {
        const apiRequest: ApiNewsletterRequest = {
            subject: request.subject,
            content: request.content,
            audience: this.mapToApiAudience(request.audience),
            scheduledAt: request.scheduledAt || undefined
        };
        return this.newslettersAdminApiService.updateNewsletter(id, apiRequest).pipe(
            map(() => void 0)
        );
    }

    deleteNewsletter(id: string): Observable<void> {
        return this.newslettersAdminApiService.deleteNewsletter(id);
    }

    scheduleNewsletter(id: string, request: ScheduleNewsletterRequest): Observable<void> {
        const apiRequest: ApiSendNewsletterRequest = {
            scheduledAt: request.scheduledAt
        };
        return this.newslettersAdminApiService.sendNewsletter(id, apiRequest).pipe(
            map(() => void 0)
        );
    }

    sendNewsletter(id: string): Observable<void> {
        return this.newslettersAdminApiService.sendNewsletter(id).pipe(
            map(() => void 0)
        );
    }

    testNewsletter(id: string): Observable<void> {
        return this.newslettersAdminApiService.testNewsletter(id).pipe(
            map(() => void 0)
        );
    }

    getNewsletterLogs(newsletterId: string, page: number = 1, pageSize: number = 10): Observable<UINewsletterLogEntry[]> {
        return this.newslettersAdminApiService.getNewsletterLogs(newsletterId, page, pageSize).pipe(
            map((response: NewsletterLogsResponse) =>
                (response.logs || []).map((log: NewsletterLogEntry) => this.mapToUINewsletterLogEntry(log))
            )
        );
    }

    private mapToApiAudience(audience: UINewsletterAudience): NewsletterAudience {
        const apiAudience: NewsletterAudience = {
            type: this.mapAudienceType(audience.type)
        };

        if (audience.userTypes && audience.userTypes.length > 0) {
            apiAudience.userTypes = audience.userTypes as UserType[];
        }

        if (audience.userIds && audience.userIds.length > 0) {
            apiAudience.userIds = audience.userIds;
        }

        return apiAudience;
    }

    private mapAudienceType(type: string): NewsletterAudience.TypeEnum {
        switch (type) {
            case 'ALL':
                return NewsletterAudience.TypeEnum.All;
            case 'USER_TYPES':
                return NewsletterAudience.TypeEnum.UserTypes;
            case 'SPECIFIC_USERS':
                return NewsletterAudience.TypeEnum.SpecificUsers;
            default:
                return NewsletterAudience.TypeEnum.All;
        }
    }

    private mapToUINewsletter(newsletter: Newsletter): UINewsletter {
        return {
            id: newsletter.id || '',
            subject: newsletter.subject || '',
            content: newsletter.content || '',
            status: (newsletter.status as NewsletterStatus) || NewsletterStatus.DRAFT,
            createdAt: newsletter.createdAt || '',
            updatedAt: newsletter.updatedAt || '',
            scheduledAt: newsletter.scheduledAt || undefined,
            sentAt: newsletter.sentAt || undefined,
            createdBy: newsletter.createdBy || '',
            recipientCount: newsletter.recipientCount || undefined,
            audience: newsletter.audience || undefined
        };
    }

    private mapToUINewsletterLogEntry(log: NewsletterLogEntry): UINewsletterLogEntry {
        // Parse the message to extract user information
        const message = log.message || '';
        const userEmailMatch = message.match(/for user ([^\s]+)/);
        const userEmail = userEmailMatch ? userEmailMatch[1] : 'unknown@example.com';

        // Determine status from level and message
        let status: 'PENDING' | 'SENT' | 'FAILED' | 'BOUNCED' = 'PENDING';
        if (log.level === 'ERROR') {
            status = 'FAILED';
        } else if (log.level === 'INFO' && message.toLowerCase().includes('sent')) {
            status = 'SENT';
        } else if (message.toLowerCase().includes('bounced')) {
            status = 'BOUNCED';
        }

        return {
            id: log.id || '',
            newsletterId: log.newsletterId || '',
            userId: userEmail.split('@')[0], // Extract user ID from email
            userEmail: userEmail,
            status: status,
            sentAt: status === 'SENT' ? log.timestamp : undefined,
            errorMessage: log.level === 'ERROR' ? log.details || log.message : undefined,
            createdAt: log.timestamp || ''
        };
    }
}
