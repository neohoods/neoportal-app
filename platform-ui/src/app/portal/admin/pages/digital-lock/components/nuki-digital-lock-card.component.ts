import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiButton, TuiIcon } from '@taiga-ui/core';

import { UIDigitalLock } from '../../../../../models/UIDigitalLock';
import { Space } from '../../../../spaces/services/spaces.service';

@Component({
    selector: 'app-nuki-digital-lock-card',
    standalone: true,
    imports: [
        CommonModule,
        TuiButton,
        TuiIcon,
        TranslateModule
    ],
    templateUrl: './nuki-digital-lock-card.component.html',
    styleUrls: ['./nuki-digital-lock-card.component.scss']
})
export class NukiDigitalLockCardComponent {
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
        const diffMinutes = Math.floor(diffMs / (1000 * 60));
        const diffHours = Math.floor(diffMinutes / 60);
        const diffDays = Math.floor(diffHours / 24);

        if (diffMinutes < 1) {
            return this.translate.instant('digitalLock.admin.justNow') || 'À l\'instant';
        } else if (diffMinutes < 60) {
            return this.translate.instant('digitalLock.admin.minutesAgo', { minutes: diffMinutes }) || `Il y a ${diffMinutes} min`;
        } else if (diffHours < 24) {
            return this.translate.instant('digitalLock.admin.hoursAgo', { hours: diffHours }) || `Il y a ${diffHours}h`;
        } else {
            return this.translate.instant('digitalLock.admin.daysAgo', { days: diffDays }) || `Il y a ${diffDays}j`;
        }
    }

    getBatteryLevel(): number | undefined {
        return this.lock.batteryLevel;
    }

    getConnectionStatus(): string {
        if (!this.lock.lastSeen) {
            return this.translate.instant('digitalLock.admin.offline');
        }

        const lastSeen = new Date(this.lock.lastSeen);
        const now = new Date();
        const diffMs = now.getTime() - lastSeen.getTime();
        const diffMinutes = Math.floor(diffMs / (1000 * 60));

        return diffMinutes < 10 ?
            this.translate.instant('digitalLock.admin.online') :
            this.translate.instant('digitalLock.admin.offline');
    }

    getOnlineStatusIcon(): string {
        return this.isOnline() ? '@tui.wifi' : '@tui.wifi-off';
    }

    isOnline(): boolean {
        if (!this.lock.lastSeen) return false;

        const lastSeen = new Date(this.lock.lastSeen);
        const now = new Date();
        const diffMs = now.getTime() - lastSeen.getTime();
        const diffMinutes = Math.floor(diffMs / (1000 * 60));

        return diffMinutes < 10;
    }

    getLastSeen(): string | undefined {
        return this.lock.lastSeen;
    }

    getSpaceName(): string {
        const space = this.spaces.find(s => s.id === this.lock.spaceId);
        return space?.name || this.translate.instant('digitalLock.admin.noAssociatedSpaces');
    }

    getAssociatedSpaces(): Space[] {
        if (!this.lock.spaceId) return [];
        return this.spaces.filter(space => space.id === this.lock.spaceId);
    }

    getSpaceType(spaceId: string): string {
        const space = this.spaces.find(s => s.id === spaceId);
        return space?.type || '';
    }

    getSpaceStatusColor(status: string): string {
        switch (status) {
            case 'ACTIVE': return 'var(--tos-text-positive)';
            case 'INACTIVE': return 'var(--tos-text-secondary)';
            case 'MAINTENANCE': return 'var(--tos-text-warning)';
            default: return 'var(--tos-text-secondary)';
        }
    }

    getSpaceStatusIcon(status: string): string {
        switch (status) {
            case 'ACTIVE': return '@tui.check-circle';
            case 'INACTIVE': return '@tui.pause-circle';
            case 'MAINTENANCE': return '@tui.settings';
            default: return '@tui.help-circle';
        }
    }

    getTranslatedSpaceStatus(status: string): string {
        return this.translate.instant('spaces.status.' + status.toLowerCase());
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