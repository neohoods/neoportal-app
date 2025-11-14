export enum UINotificationType {
    ADMIN_NEW_USER = 'admin-new-user',
    NEW_ANNOUNCEMENT = 'new-announcement',
    RESERVATION = 'reservation',
    UNIT_INVITATION = 'unit-invitation',
    UNIT_JOIN_REQUEST = 'unit-join-request',
}

export interface UINotification {
    id: string;
    author: string;
    date: string;
    type: UINotificationType;
    alreadyRead: boolean;
    payload: any | undefined;
    translationKey?: string;
}
