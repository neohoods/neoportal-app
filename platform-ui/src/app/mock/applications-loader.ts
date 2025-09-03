import { UIAnnouncement, UIAnnouncementCategory } from '../models/UIAnnoncements';
import { UIApplication } from '../models/UIApplication';
import applicationsData from './applications.json';


export const loadApplicationsData = (): UIApplication[] => {
    return applicationsData;
};
