export enum UINotificationType {
    ADMIN_NEW_USER = 'admin-new-user',
}

export interface UINotification {
    id: string;
    author: string;
    date: string;
    type: UINotificationType;
    alreadyRead: boolean;
    payload: any | undefined;
}
