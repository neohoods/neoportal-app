export interface UIUser {
    id: string;
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    avatarUrl?: string;
    phone?: string;
    isEmailVerified: boolean;
    disabled: boolean;
    preferredLanguage: string;
    createdAt: string;
    updatedAt?: string;
    flatNumber?: string;
    streetAddress?: string;
    city?: string;
    postalCode?: string;
    country?: string;
    type: UIUserType;
    roles: string[];
    profileSharingConsent?: boolean;
    primaryUnitId?: string;
}

export enum UIUserType {
    OWNER = 'OWNER',
    TENANT = 'TENANT',
    ADMIN = 'ADMIN',
    MODERATOR = 'MODERATOR',
    LANDLORD = 'LANDLORD',
    SYNDIC = 'SYNDIC',
    EXTERNAL = 'EXTERNAL'
}