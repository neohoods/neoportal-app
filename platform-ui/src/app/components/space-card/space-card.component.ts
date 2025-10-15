import { CommonModule } from '@angular/common';
import { Component, EventEmitter, inject, Input, Output, signal } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiButton, TuiHint, TuiIcon } from '@taiga-ui/core';
import { UIUser } from '../../models/UIUser';
import { Space } from '../../portal/spaces/services/spaces.service';

@Component({
    selector: 'app-space-card',
    standalone: true,
    imports: [
        CommonModule,
        TranslateModule,
        TuiButton,
        TuiIcon,
        TuiHint
    ],
    templateUrl: './space-card.component.html',
    styleUrls: ['./space-card.component.scss']
})
export class SpaceCardComponent {
    private translate = inject(TranslateService);

    @Input() space!: Space;
    @Input() currentUser?: UIUser;
    @Input() showActions: boolean = true;
    @Input() compact: boolean = false;

    @Output() spaceClick = new EventEmitter<Space>();
    @Output() reserveClick = new EventEmitter<Space>();
    @Output() favoriteClick = new EventEmitter<Space>();

    isLoading = signal(false);

    onSpaceClick() {
        this.spaceClick.emit(this.space);
    }

    onReserveClick(event: Event) {
        event.stopPropagation();
        this.isLoading.set(true);
        this.reserveClick.emit(this.space);
        // Simulate loading
        setTimeout(() => this.isLoading.set(false), 1000);
    }

    onFavoriteClick(event: Event) {
        event.stopPropagation();
        this.favoriteClick.emit(this.space);
    }

    getSpaceTypeIcon(type: string): string {
        switch (type) {
            case 'GUEST_ROOM': return '@tui.home';
            case 'COMMON_ROOM': return '@tui.users';
            case 'COWORKING': return '@tui.monitor';
            case 'PARKING': return '@tui.car';
            default: return '@tui.map-pin';
        }
    }

    getSpaceTypeLabel(type: string): string {
        return this.translate.instant(`spaces.types.${type}`);
    }

    getSpaceStatusLabel(status: string): string {
        return this.translate.instant(`spaces.status.${status}`);
    }

    getStatusClass(status: string): string {
        return `status-${status.toLowerCase()}`;
    }

    // Quota methods removed - no longer tracking usage

    getDurationText(): string {
        const min = this.space.rules.minDurationDays;
        const max = this.space.rules.maxDurationDays;
        const daysLabel = this.translate.instant('spaces.admin.days');

        if (min === max) {
            return `${min} ${daysLabel}`;
        }

        return `${min}-${max} ${daysLabel}`;
    }

    getCleaningFeeText(): string {
        if (!this.space.pricing.cleaningFee || this.space.pricing.cleaningFee === 0) {
            return this.translate.instant('spaces.card.cleaningIncluded');
        }
        return `+${this.space.pricing.cleaningFee}€ ${this.translate.instant('spaces.card.cleaning')}`;
    }

    getPriceText(): string {
        if (!this.currentUser) {
            // Fallback to tenant price if no user info
            if (this.space.pricing.tenantPrice === 0) {
                return this.translate.instant('spaces.card.free');
            }
            return `${this.space.pricing.tenantPrice}€`;
        }

        // Show owner price for owners, tenant price for tenants
        const isOwner = this.currentUser.type === 'OWNER';
        const price = isOwner ? this.space.pricing.ownerPrice : this.space.pricing.tenantPrice;

        if (price === 0) {
            return this.translate.instant('spaces.card.free');
        }
        return `${price}€`;
    }

    getAllowedDaysText(): string {
        if (!this.space.rules.allowedDays || this.space.rules.allowedDays.length === 0) {
            return '';
        }

        const dayMap: { [key: string]: string } = {
            'MONDAY': this.translate.instant('spaces.days.monday'),
            'TUESDAY': this.translate.instant('spaces.days.tuesday'),
            'WEDNESDAY': this.translate.instant('spaces.days.wednesday'),
            'THURSDAY': this.translate.instant('spaces.days.thursday'),
            'FRIDAY': this.translate.instant('spaces.days.friday'),
            'SATURDAY': this.translate.instant('spaces.days.saturday'),
            'SUNDAY': this.translate.instant('spaces.days.sunday')
        };

        const dayOrder = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
        const allowedDays = this.space.rules.allowedDays;

        // Vérifier si c'est une séquence consécutive
        const indices = allowedDays.map(day => dayOrder.indexOf(day)).sort((a, b) => a - b);

        // Vérifier si les indices sont consécutifs
        let isConsecutive = true;
        for (let i = 1; i < indices.length; i++) {
            if (indices[i] !== indices[i - 1] + 1) {
                isConsecutive = false;
                break;
            }
        }

        // Si consécutif et au moins 3 jours, afficher sous forme de plage
        if (isConsecutive && indices.length >= 3) {
            const firstDay = dayOrder[indices[0]];
            const lastDay = dayOrder[indices[indices.length - 1]];
            return `${dayMap[firstDay]} - ${dayMap[lastDay]}`;
        }

        // Sinon, afficher la liste complète
        const days = allowedDays.map(day => dayMap[day] || day).join(', ');
        return days;
    }

    getMaxDurationText(): string {
        const max = this.space.rules.maxDurationDays;
        const daysLabel = this.translate.instant('spaces.admin.days');
        return `${max} ${daysLabel}`;
    }

    getCapacityText(): string {
        if (!this.space.quota) return '';
        return `${this.space.quota.max} ${this.translate.instant('spaces.admin.people')}`;
    }

    getHoursText(): string {
        if (!this.space.rules.allowedHours) return '';
        return `${this.space.rules.allowedHours.start} - ${this.space.rules.allowedHours.end}`;
    }

    // Déterminer quelles infos afficher selon le type
    shouldShowPrice(): boolean {
        return ['PARKING', 'GUEST_ROOM'].includes(this.space.type);
    }

    shouldShowMaxDuration(): boolean {
        return this.space.type === 'PARKING';
    }

    shouldShowCapacity(): boolean {
        return this.space.type === 'COMMON_ROOM';
    }

    shouldShowHours(): boolean {
        return ['COMMON_ROOM', 'COWORKING'].includes(this.space.type);
    }

    shouldShowAllowedDays(): boolean {
        return ['COWORKING', 'COMMON_ROOM'].includes(this.space.type) &&
            this.space.rules.allowedDays &&
            this.space.rules.allowedDays.length > 0;
    }

    getQuotaText(): string {
        if (!this.space.quota) {
            return '';
        }

        const quota = this.space.quota;
        const period = quota.period ?
            this.translate.instant(`spaces.quota.period.${quota.period.toLowerCase()}`) :
            this.translate.instant('spaces.quota.period.year');

        // TODO: Calculate remaining quota from existing reservations
        // For now, show total quota
        return `${quota.max} ${this.translate.instant('spaces.quota.per')} ${period}`;
    }

    hasQuota(): boolean {
        return !!this.space.quota && this.space.type === 'GUEST_ROOM';
    }
}