
export enum UIAnnouncementCategory {
    CommunityEvent = 'CommunityEvent',
    LostAndFound = 'LostAndFound',
    SafetyAlert = 'SafetyAlert',
    MaintenanceNotice = 'MaintenanceNotice',
    SocialGathering = 'SocialGathering',
    Other = 'Other'
}

export const CATEGORY_ICONS: { [key in UIAnnouncementCategory]: string } = {
    [UIAnnouncementCategory.CommunityEvent]: '@tui.calendar-heart',
    [UIAnnouncementCategory.LostAndFound]: '@tui.search-x',
    [UIAnnouncementCategory.SafetyAlert]: '@tui.triangle-alert',
    [UIAnnouncementCategory.MaintenanceNotice]: '@tui.construction',
    [UIAnnouncementCategory.SocialGathering]: '@tui.users',
    [UIAnnouncementCategory.Other]: '@tui.message-circle',
};

export const CATEGORY_TRANSLATIONS: { [key in UIAnnouncementCategory]: string } = {
    [UIAnnouncementCategory.CommunityEvent]: 'announcement-category.community-event',
    [UIAnnouncementCategory.LostAndFound]: 'announcement-category.lost-and-found',
    [UIAnnouncementCategory.SafetyAlert]: 'announcement-category.safety-alert',
    [UIAnnouncementCategory.MaintenanceNotice]: 'announcement-category.maintenance-notice',
    [UIAnnouncementCategory.SocialGathering]: 'announcement-category.social-gathering',
    [UIAnnouncementCategory.Other]: 'announcement-category.other',
};

export const CATEGORY_COLORS: { [key in UIAnnouncementCategory]: string } = {
    [UIAnnouncementCategory.CommunityEvent]: '#10b981', // Green - festive/positive
    [UIAnnouncementCategory.LostAndFound]: '#3b82f6', // Blue - attention-getting
    [UIAnnouncementCategory.SafetyAlert]: '#ef4444', // Red - urgent/danger
    [UIAnnouncementCategory.MaintenanceNotice]: '#fd905d', // Amber - informational
    [UIAnnouncementCategory.SocialGathering]: '#8b5cf6', // Purple - social/fun
    [UIAnnouncementCategory.Other]: '#6b7280', // Gray - neutral
};

export interface CategoryInfo {
    category: UIAnnouncementCategory;
    icon: string;
    translationKey: string;
    color: string;
}

export const CATEGORY_INFO: CategoryInfo[] = Object.values(UIAnnouncementCategory).map(category => ({
    category,
    icon: CATEGORY_ICONS[category],
    translationKey: CATEGORY_TRANSLATIONS[category],
    color: CATEGORY_COLORS[category]
}));

export interface UIAnnouncement {
    id: string;
    title: string;
    content: string;
    createdAt: string;
    updatedAt: string;
    category: UIAnnouncementCategory;
}

export interface UIPaginatedAnnouncementsResponse {
    totalPages: number;
    totalItems: number;
    currentPage: number;
    itemsPerPage: number;
    announcements: UIAnnouncement[];
}

export interface UIPaginationParams {
    page?: number;
    pageSize?: number;
}