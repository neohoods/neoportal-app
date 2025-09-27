export enum EmailTemplateType {
    WELCOME = 'WELCOME'
}

export interface UIEmailTemplate {
    id: string;
    type: EmailTemplateType;
    name: string;
    subject: string;
    content: string;
    isActive: boolean;
    createdAt: string;
    updatedAt: string;
    createdBy: string;
    description?: string;
}

export interface CreateEmailTemplateRequest {
    type: EmailTemplateType;
    name: string;
    subject: string;
    content: string;
    isActive?: boolean;
    description?: string;
}

export interface UpdateEmailTemplateRequest {
    type: EmailTemplateType;
    name: string;
    subject: string;
    content: string;
    isActive?: boolean;
    description?: string;
}

export interface TestEmailTemplateRequest {
    // Optional: can be empty for testing with current user
}

export interface TestEmailTemplateResponse {
    message: string;
    sentTo: string;
}
