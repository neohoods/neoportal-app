import { UIAnnouncement, UIAnnouncementCategory } from '../models/UIAnnoncements';
import annoncementsData from './annoncements.json';


export const loadAnnouncementsData = (): UIAnnouncement[] => {
    return annoncementsData.map(announcement => ({
        ...announcement,
        category: announcement.category as UIAnnouncementCategory,
    }));
};
