export interface UIDigitalLock {
    id: string;
    name: string;
    type: 'TTLOCK' | 'NUKI' | 'YALE';
    status: 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE' | 'ERROR';
    spaceId?: string;
    batteryLevel?: number;
    lastSeen?: string;
    ttlockConfig?: UITtlockConfig | null;
    nukiConfig?: UINukiConfig | null;
    createdAt: string;
    updatedAt: string;
}

export interface UITtlockConfig {
    id: string;
    deviceId: string;
    location: string;
    batteryLevel?: number;
    signalStrength?: number;
    lastSeen?: string;
}

export interface UINukiConfig {
    id: string;
    deviceId: string;
    token: string;
    batteryLevel?: number;
    lastSeen?: string;
}

export interface UIAccessCode {
    code: string;
    generatedAt: string;
    expiresAt: string;
    isActive: boolean;
}

// Helper type guards
export function isTtlockConfig(config: any): config is UITtlockConfig {
    return config && typeof config.deviceId === 'string' && typeof config.location === 'string';
}

export function isNukiConfig(config: any): config is UINukiConfig {
    return config && typeof config.deviceId === 'string' && typeof config.token === 'string';
}

