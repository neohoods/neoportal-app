import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
    CreateEmailTemplateRequest,
    EmailTemplateType,
    TestEmailTemplateRequest,
    TestEmailTemplateResponse,
    UIEmailTemplate,
    UpdateEmailTemplateRequest
} from '../../../models/UIEmailTemplate';

@Injectable({
    providedIn: 'root'
})
export class EmailTemplatesService {
    private readonly baseUrl = '/api/admin/email-templates';

    constructor(private http: HttpClient) { }

    /**
     * Get all email templates, optionally filtered by type
     */
    getEmailTemplates(type?: EmailTemplateType): Observable<UIEmailTemplate[]> {
        let params = new HttpParams();
        if (type) {
            params = params.set('type', type);
        }
        return this.http.get<UIEmailTemplate[]>(this.baseUrl, { params });
    }

    /**
     * Get a specific email template by ID
     */
    getEmailTemplate(id: string): Observable<UIEmailTemplate> {
        return this.http.get<UIEmailTemplate>(`${this.baseUrl}/${id}`);
    }

    /**
     * Create a new email template
     */
    createEmailTemplate(request: CreateEmailTemplateRequest): Observable<UIEmailTemplate> {
        return this.http.post<UIEmailTemplate>(this.baseUrl, request);
    }

    /**
     * Update an existing email template
     */
    updateEmailTemplate(id: string, request: UpdateEmailTemplateRequest): Observable<UIEmailTemplate> {
        return this.http.put<UIEmailTemplate>(`${this.baseUrl}/${id}`, request);
    }

    /**
     * Delete an email template
     */
    deleteEmailTemplate(id: string): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${id}`);
    }

    /**
     * Send a test email using the template
     */
    testEmailTemplate(id: string, request: TestEmailTemplateRequest = {}): Observable<TestEmailTemplateResponse> {
        return this.http.post<TestEmailTemplateResponse>(`${this.baseUrl}/${id}/test`, request);
    }

    /**
     * Activate an email template (deactivates others of the same type)
     */
    activateEmailTemplate(id: string): Observable<UIEmailTemplate> {
        return this.http.post<UIEmailTemplate>(`${this.baseUrl}/${id}/activate`, {});
    }
}
