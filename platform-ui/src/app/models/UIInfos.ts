


export interface UIInfo {
    id: string;
    nextAGDate: string;
    rulesUrl: string;
    delegates: Array<UIDelegate>;
    contactNumbers: UIInfoContactNumbers;
}

export interface UIDelegate {
    building?: string;
    firstName?: string;
    lastName?: string;
    email?: string;
    matrixUser?: string;
}

export interface UIInfoContactNumbers {
    syndic?: UIContactNumber;
    emergency?: Array<UIContactNumber>;
}

export interface UIContactNumber {
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

