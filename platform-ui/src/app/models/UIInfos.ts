


export interface UIInfo {
    id: string;
    nextAGDate: string | null;
    rulesUrl: string;
    delegates: Array<UIDelegate>;
    contactNumbers: Array<UIContactNumber>;
}

export interface UIDelegate {
    building?: string;
    firstName?: string;
    lastName?: string;
    email?: string;
    matrixUser?: string;
}


export interface UIContactNumber {
    contactType?: 'syndic' | 'emergency' | 'maintenance';
    type?: string;
    description?: string;
    availability?: string;
    responseTime?: string;
    name?: string;
    phoneNumber?: string;
    email?: string;
    officeHours?: string;
    address?: string;
}

