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
    properties?: UIProperty[];
    profileSharingConsent?: boolean;
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

export interface UIProperty {
    id: string;
    name: string;
    type: UIPropertyType;
    address: string;
    city: string;
    postalCode: string;
    country: string;
    flatNumber?: string;
    isPrimary: boolean;
}

export enum UIPropertyType {
    APARTMENT = 'APARTMENT',
    HOUSE = 'HOUSE',
    OFFICE = 'OFFICE',
    COMMERCIAL = 'COMMERCIAL'
}