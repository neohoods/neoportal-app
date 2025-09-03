import { UINotification, UINotificationType } from '../models/UINotification';
import notificationsData from './notifications.json';


export const loadNotificationsData = (): UINotification[] => {
    return notificationsData.map(notification => ({
        ...notification,
        type: notification.type as unknown as UINotificationType,
        payload: {},
    }));
};
