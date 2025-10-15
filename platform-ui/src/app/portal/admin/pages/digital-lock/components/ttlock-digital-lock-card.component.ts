import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiButton, TuiIcon } from '@taiga-ui/core';

import { UIDigitalLock } from '../../../../../models/UIDigitalLock';
import { Space } from '../../../../spaces/services/spaces.service';

@Component({
    selector: 'app-ttlock-digital-lock-card',
    standalone: true,
    imports: [
        CommonModule,
        TuiButton,
        TuiIcon,
        TranslateModule
    ],
    templateUrl: './ttlock-digital-lock-card.component.html',
    styleUrls: ['./ttlock-digital-lock-card.component.scss']
})
export class TtlockDigitalLockCardComponent {
    @Input() lock!: UIDigitalLock;
    @Input() spaces: Space[] = [];
    @Output() generateCode = new EventEmitter<void>();
    @Output() toggleStatus = new EventEmitter<void>();
    @Output() edit = new EventEmitter<void>();
    @Output() delete = new EventEmitter<void>();

    constructor(private translate: TranslateService) { }

    getStatusIcon(status: string): string {
        switch (status) {
            case 'ACTIVE': return '@tui.check-circle';
            case 'INACTIVE': return '@tui.pause-circle';
            case 'ERROR': return '@tui.alert-circle';
            default: return '@tui.help-circle';
        }
    }

    getStatusColor(status: string): string {
        switch (status) {
            case 'ACTIVE': return 'var(--tos-text-positive)';
            case 'INACTIVE': return 'var(--tos-text-secondary)';
            case 'ERROR': return 'var(--tos-text-negative)';
            default: return 'var(--tos-text-secondary)';
        }
    }

    formatLastSeen(lastSeen: string | undefined): string {
        if (!lastSeen) return this.translate.instant('digitalLock.admin.never') || 'Jamais';

        const date = new Date(lastSeen);
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffMins = Math.floor(diffMs / 60000);

        if (diffMins < 1) return this.translate.instant('digitalLock.admin.justNow') || 'À l\'instant';
        if (diffMins < 60) return this.translate.instant('digitalLock.admin.minutesAgo', { minutes: diffMins }) || `Il y a ${diffMins} min`;

        const diffHours = Math.floor(diffMins / 60);
        if (diffHours < 24) return this.translate.instant('digitalLock.admin.hoursAgo', { hours: diffHours }) || `Il y a ${diffHours}h`;

        const diffDays = Math.floor(diffHours / 24);
        return this.translate.instant('digitalLock.admin.daysAgo', { days: diffDays }) || `Il y a ${diffDays}j`;
    }

    getTranslatedSpaceType(type: string): string {
        const typeMap: { [key: string]: string } = {
            'GUEST_ROOM': 'spaces.spaceType.guestRoom',
            'COMMON_ROOM': 'spaces.spaceType.commonRoom',
            'COWORKING': 'spaces.spaceType.coworking',
            'PARKING': 'spaces.spaceType.parking'
        };
        return this.translate.instant(typeMap[type] || 'spaces.spaceType.unknown');
    }

    getTranslatedSpaceStatus(status: string): string {
        const statusMap: { [key: string]: string } = {
            'ACTIVE': 'spaces.admin.status.active',
            'INACTIVE': 'spaces.admin.status.inactive',
            'MAINTENANCE': 'spaces.admin.status.maintenance'
        };
        return this.translate.instant(statusMap[status] || 'spaces.admin.status.unknown');
    }

    getSpaceStatusIcon(status: string): string {
        switch (status) {
            case 'ACTIVE': return '@tui.check-circle';
            case 'INACTIVE': return '@tui.pause-circle';
            case 'MAINTENANCE': return '@tui.tool';
            default: return '@tui.help-circle';
        }
    }

    getSpaceStatusColor(status: string): string {
        switch (status) {
            case 'ACTIVE': return 'var(--tos-text-positive)';
            case 'INACTIVE': return 'var(--tos-text-secondary)';
            case 'MAINTENANCE': return 'var(--tos-text-warning)';
            default: return 'var(--tos-text-secondary)';
        }
    }

    getSpaceName(spaceId: string): string {
        const space = this.spaces.find(s => s.id === spaceId);
        return space ? space.name : 'Espace inconnu';
    }

    getSpaceType(spaceId: string): string {
        const space = this.spaces.find(s => s.id === spaceId);
        return space ? this.getTranslatedSpaceType(space.type) : 'N/A';
    }

    getSpaceStatus(spaceId: string): string {
        const space = this.spaces.find(s => s.id === spaceId);
        return space ? space.status : 'UNKNOWN';
    }

    getAssociatedSpaces(): Space[] {
        return this.spaces.filter(space => (space as any).digitalLockId === this.lock.id);
    }

    getDeviceId(): string {
        if (this.lock.type === 'TTLOCK' && this.lock.ttlockConfig) {
            return this.lock.ttlockConfig.deviceId;
        }
        if (this.lock.type === 'NUKI' && this.lock.nukiConfig) {
            return this.lock.nukiConfig.deviceId;
        }
        return 'N/A';
    }

    getLocation(): string {
        if (this.lock.type === 'TTLOCK' && this.lock.ttlockConfig) {
            return this.lock.ttlockConfig.location;
        }
        return 'N/A';
    }

    getBatteryLevel(): number | undefined {
        if (this.lock.type === 'TTLOCK' && this.lock.ttlockConfig) {
            return this.lock.ttlockConfig.batteryLevel;
        }
        if (this.lock.type === 'NUKI' && this.lock.nukiConfig) {
            return this.lock.nukiConfig.batteryLevel;
        }
        return undefined;
    }

    getSignalStrength(): number | undefined {
        if (this.lock.type === 'TTLOCK' && this.lock.ttlockConfig) {
            return this.lock.ttlockConfig.signalStrength;
        }
        return undefined;
    }

    getLastSeen(): string | undefined {
        if (this.lock.type === 'TTLOCK' && this.lock.ttlockConfig) {
            return this.lock.ttlockConfig.lastSeen;
        }
        if (this.lock.type === 'NUKI' && this.lock.nukiConfig) {
            return this.lock.nukiConfig.lastSeen;
        }
        return undefined;
    }

    onGenerateCode(): void {
        this.generateCode.emit();
    }

    onToggleStatus(): void {
        this.toggleStatus.emit();
    }

    onEdit(): void {
        this.edit.emit();
    }

    onDelete(): void {
        this.delete.emit();
    }
}
