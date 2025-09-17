import { Injectable } from '@angular/core';
import { delay, Observable } from 'rxjs';
import { NewslettersLoader } from '../../../../mock/newsletters-loader';
import {
    CreateNewsletterRequest,
    NewsletterStatus,
    PaginatedNewslettersResponse,
    ScheduleNewsletterRequest,
    UINewsletter,
    UpdateNewsletterRequest
} from '../../../../models/UINewsletter';
import { NewslettersService } from '../newsletters.service';

@Injectable({
    providedIn: 'root',
})
export class MockNewslettersService implements NewslettersService {
    private newsletters: UINewsletter[] = [];
    private initialized = false;

    private async initializeData(): Promise<void> {
        if (!this.initialized) {
            this.newsletters = await NewslettersLoader.loadNewsletters();
            this.initialized = true;
        }
    }

    getNewsletters(page: number = 1, pageSize: number = 10): Observable<PaginatedNewslettersResponse> {
        return new Observable<PaginatedNewslettersResponse>(observer => {
            this.initializeData().then(() => {
                const startIndex = (page - 1) * pageSize;
                const endIndex = startIndex + pageSize;
                const paginatedNewsletters = this.newsletters.slice(startIndex, endIndex);

                observer.next({
                    totalPages: Math.ceil(this.newsletters.length / pageSize),
                    totalItems: this.newsletters.length,
                    currentPage: page,
                    itemsPerPage: pageSize,
                    newsletters: paginatedNewsletters
                });
                observer.complete();
            }).catch(error => observer.error(error));
        }).pipe(delay(500));
    }

    getNewsletter(id: string): Observable<UINewsletter> {
        return new Observable<UINewsletter>(observer => {
            this.initializeData().then(() => {
                const newsletter = this.newsletters.find(n => n.id === id);
                if (!newsletter) {
                    observer.error(new Error(`Newsletter with id ${id} not found`));
                } else {
                    observer.next(newsletter);
                    observer.complete();
                }
            }).catch(error => observer.error(error));
        }).pipe(delay(300));
    }

    createNewsletter(request: CreateNewsletterRequest): Observable<UINewsletter> {
        return new Observable<UINewsletter>(observer => {
            this.initializeData().then(() => {
                const newNewsletter: UINewsletter = {
                    id: (this.newsletters.length + 1).toString(),
                    subject: request.subject,
                    content: request.content,
                    status: NewsletterStatus.DRAFT,
                    createdAt: new Date().toISOString(),
                    updatedAt: new Date().toISOString(),
                    createdBy: 'f71c870e-9daa-4991-accd-61f3c3c14fa2',
                    audience: request.audience
                };

                this.newsletters.unshift(newNewsletter);
                observer.next(newNewsletter);
                observer.complete();
            }).catch(error => observer.error(error));
        }).pipe(delay(500));
    }

    updateNewsletter(id: string, request: UpdateNewsletterRequest): Observable<void> {
        return new Observable<void>(observer => {
            this.initializeData().then(() => {
                const index = this.newsletters.findIndex(n => n.id === id);
                if (index === -1) {
                    observer.error(new Error(`Newsletter with id ${id} not found`));
                } else {
                    this.newsletters[index] = {
                        ...this.newsletters[index],
                        subject: request.subject,
                        content: request.content,
                        audience: request.audience,
                        updatedAt: new Date().toISOString()
                    };
                    observer.next();
                    observer.complete();
                }
            }).catch(error => observer.error(error));
        }).pipe(delay(500));
    }

    deleteNewsletter(id: string): Observable<void> {
        return new Observable<void>(observer => {
            this.initializeData().then(() => {
                const index = this.newsletters.findIndex(n => n.id === id);
                if (index === -1) {
                    observer.error(new Error(`Newsletter with id ${id} not found`));
                } else {
                    this.newsletters.splice(index, 1);
                    observer.next();
                    observer.complete();
                }
            }).catch(error => observer.error(error));
        }).pipe(delay(500));
    }

    scheduleNewsletter(id: string, request: ScheduleNewsletterRequest): Observable<void> {
        return new Observable<void>(observer => {
            this.initializeData().then(() => {
                const index = this.newsletters.findIndex(n => n.id === id);
                if (index === -1) {
                    observer.error(new Error(`Newsletter with id ${id} not found`));
                } else {
                    this.newsletters[index] = {
                        ...this.newsletters[index],
                        status: NewsletterStatus.SCHEDULED,
                        scheduledAt: request.scheduledAt,
                        updatedAt: new Date().toISOString()
                    };
                    observer.next();
                    observer.complete();
                }
            }).catch(error => observer.error(error));
        }).pipe(delay(500));
    }

    sendNewsletter(id: string): Observable<void> {
        return new Observable<void>(observer => {
            this.initializeData().then(() => {
                const index = this.newsletters.findIndex(n => n.id === id);
                if (index === -1) {
                    observer.error(new Error(`Newsletter with id ${id} not found`));
                } else {
                    this.newsletters[index] = {
                        ...this.newsletters[index],
                        status: NewsletterStatus.SENT,
                        sentAt: new Date().toISOString(),
                        updatedAt: new Date().toISOString(),
                        recipientCount: 150 // Mock recipient count
                    };
                    observer.next();
                    observer.complete();
                }
            }).catch(error => observer.error(error));
        }).pipe(delay(1000));
    }

    testNewsletter(id: string): Observable<void> {
        return new Observable<void>(observer => {
            this.initializeData().then(() => {
                const newsletter = this.newsletters.find(n => n.id === id);
                if (!newsletter) {
                    observer.error(new Error(`Newsletter with id ${id} not found`));
                } else {
                    // Simuler l'envoi d'un test
                    console.log(`Sending test newsletter: ${newsletter.subject} to admin email`);
                    observer.next();
                    observer.complete();
                }
            }).catch(error => observer.error(error));
        }).pipe(delay(800));
    }
}
