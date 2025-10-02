export interface UIUser {
    id: string;
    username: string;

    firstName: string;
    lastName: string;

    email: string;
    isEmailVerified: boolean;

    avatarUrl?: string;

    disabled: boolean;
    preferredLanguage?: string;
    profileSharingConsent?: boolean;
    createdAt?: string;

    type: UIUserType;
    roles: string[];
    properties: UIProperty[];

    //address
    flatNumber?: string;
    streetAddress: string;
    city: string;
    postalCode: string;
    country: string;
}

export interface UIProperty {
    type: UIPropertyType;
    name: string;
}

export enum UIUserType {
    ADMIN = 'ADMIN',
    OWNER = 'OWNER',
    LANDLORD = 'LANDLORD',
    TENANT = 'TENANT',
    SYNDIC = 'SYNDIC',
    EXTERNAL = 'EXTERNAL',
    CONTRACTOR = 'CONTRACTOR',
    COMMERCIAL_PROPERTY_OWNER = 'COMMERCIAL_PROPERTY_OWNER',
    GUEST = 'GUEST',
}

export enum UIPropertyType {
    APARTMENT = 'APARTMENT',
    GARAGE = 'GARAGE',
    PARKING = 'PARKING',
    COMMERCIAL = 'COMMERCIAL',
    OTHER = 'OTHER',
}