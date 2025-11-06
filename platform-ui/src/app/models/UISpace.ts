import { CleaningSettings, QuotaInfo, Space, SpaceImage, SpacePricing, SpaceRules, SpaceStatus, SpaceType } from '../api-client';

export interface UISpace {
    id: string;
    name: string;
    type: SpaceType;
    status: SpaceStatus;
    description: string;
    instructions: string;
    pricing: SpacePricing;
    rules: SpaceRules;
    images: SpaceImage[];
    quota?: QuotaInfo;
    digitalLockId?: string | null;
    accessCodeEnabled?: boolean;
    cleaningSettings?: CleaningSettings;
    reservationCalendarUrl?: string | null;
    createdAt: string;
    updatedAt: string;
}

export function fromApiSpace(apiSpace: Space): UISpace {
    return {
        id: apiSpace.id || '',
        name: apiSpace.name || '',
        type: apiSpace.type || 'GUEST_ROOM',
        status: apiSpace.status || 'INACTIVE',
        description: apiSpace.description || '',
        instructions: apiSpace.instructions || '',
        pricing: apiSpace.pricing || { tenantPrice: 0, ownerPrice: 0, cleaningFee: 0, deposit: 0 },
        rules: apiSpace.rules || {
            minDurationDays: 1,
            maxDurationDays: 30,
            maxReservationsPerYear: 12,
            allowedDays: [],
            allowedHours: { start: '08:00', end: '20:00' },
            cleaningDays: [],
            requiresApartmentAccess: false,
            conflictWithTypes: []
        },
        images: apiSpace.images || [],
        quota: apiSpace.quota,
        digitalLockId: apiSpace.digitalLockId,
        accessCodeEnabled: apiSpace.accessCodeEnabled,
        cleaningSettings: apiSpace.cleaningSettings,
        reservationCalendarUrl: apiSpace.reservationCalendarUrl || null,
        createdAt: apiSpace.createdAt || new Date().toISOString(),
        updatedAt: apiSpace.updatedAt || new Date().toISOString()
    };
}

export function toApiSpace(uiSpace: Partial<UISpace>): Partial<Space> {
    return {
        id: uiSpace.id,
        name: uiSpace.name,
        type: uiSpace.type,
        status: uiSpace.status,
        description: uiSpace.description,
        instructions: uiSpace.instructions,
        pricing: uiSpace.pricing,
        rules: uiSpace.rules,
        images: uiSpace.images,
        quota: uiSpace.quota,
        digitalLockId: uiSpace.digitalLockId || undefined,
        accessCodeEnabled: uiSpace.accessCodeEnabled,
        cleaningSettings: uiSpace.cleaningSettings,
        createdAt: uiSpace.createdAt,
        updatedAt: uiSpace.updatedAt
    };
}

