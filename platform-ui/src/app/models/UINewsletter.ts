export interface UINewsletter {
    id: string;
    subject: string;
    content: string;
    status: NewsletterStatus;
    createdAt: string;
    updatedAt: string;
    scheduledAt?: string;
    sentAt?: string;
    createdBy: string;
    recipientCount?: number;
    audience?: UINewsletterAudience;
}

export interface UINewsletterAudience {
    type: 'ALL' | 'USER_TYPES' | 'SPECIFIC_USERS';
    userTypes?: string[];
    userIds?: string[];
    excludeUserIds?: string[];
}

export interface UINewsletterLogEntry {
    id: string;
    newsletterId: string;
    userId: string;
    userEmail: string;
    status: 'PENDING' | 'SENT' | 'FAILED' | 'BOUNCED';
    sentAt?: string;
    errorMessage?: string;
    createdAt: string;
}

export enum NewsletterStatus {
    DRAFT = 'DRAFT',
    SCHEDULED = 'SCHEDULED',
    SENDING = 'SENDING',
    SENT = 'SENT',
    CANCELLED = 'CANCELLED',
    FAILED = 'FAILED'
}

export interface CreateNewsletterRequest {
    subject: string;
    content: string;
    audience: UINewsletterAudience;
}

export interface UpdateNewsletterRequest {
    subject: string;
    content: string;
    audience: UINewsletterAudience;
}

export interface ScheduleNewsletterRequest {
    scheduledAt: string;
}

export interface PaginatedNewslettersResponse {
    totalPages: number;
    totalItems: number;
    currentPage: number;
    itemsPerPage: number;
    newsletters: UINewsletter[];
}
