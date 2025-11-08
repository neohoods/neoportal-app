import { CommonModule, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAutoColorPipe, TuiIcon, TuiInitialsPipe, TuiNotification } from '@taiga-ui/core';
import { TuiAvatar, TuiChip } from '@taiga-ui/kit';
import { UnitsHubApiService } from '../../../../../api-client/api/unitsHubApi.service';
import { Unit } from '../../../../../api-client/model/unit';
import { AUTH_SERVICE_TOKEN } from '../../../../../global.provider';
import { AuthService } from '../../../../../services/auth.service';

interface UnitWithMetadata extends Unit {
    isPrimary?: boolean;
    roleLabels?: Map<string, string>;
}

@Component({
    selector: 'app-units',
    standalone: true,
    imports: [
        CommonModule,
        DatePipe,
        TranslateModule,
        TuiNotification,
        TuiAvatar,
        TuiInitialsPipe,
        TuiAutoColorPipe,
        TuiIcon,
        TuiChip
    ],
    templateUrl: './units.component.html',
    styleUrl: './units.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class UnitsComponent implements OnInit {
    units: UnitWithMetadata[] = [];
    loading = true;
    error: string | null = null;
    private primaryUnitId: string | null = null;
    private roleLabelsCache = new Map<string, string>();

    constructor(
        private unitsApiService: UnitsHubApiService,
        private router: Router,
        private translate: TranslateService,
        @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
        private cdr: ChangeDetectorRef
    ) { }

    ngOnInit(): void {
        // Cache primary unit ID once
        const currentUser = this.authService.getCurrentUserInfo()?.user;
        this.primaryUnitId = currentUser?.primaryUnitId || null;
        
        // Pre-cache role labels
        this.roleLabelsCache.set('ADMIN', this.translate.instant('units.role.admin'));
        this.roleLabelsCache.set('MEMBER', this.translate.instant('units.role.member'));
        
        this.loadUnits();
    }

    loadUnits(): void {
        this.loading = true;
        this.error = null;
        this.cdr.markForCheck();

        this.unitsApiService.getUnits().subscribe({
            next: (units: Unit[]) => {
                // Pre-process units to add metadata
                this.units = units.map(unit => {
                    const unitWithMetadata: UnitWithMetadata = {
                        ...unit,
                        isPrimary: this.primaryUnitId === unit.id,
                        roleLabels: new Map<string, string>()
                    };
                    
                    // Pre-cache role labels for all members
                    if (unit.members) {
                        unit.members.forEach(member => {
                            if (member.role && !unitWithMetadata.roleLabels!.has(member.role)) {
                                unitWithMetadata.roleLabels!.set(
                                    member.role,
                                    this.getRoleLabel(member.role)
                                );
                            }
                        });
                    }
                    
                    return unitWithMetadata;
                });
                
                this.loading = false;
                this.cdr.markForCheck();
            },
            error: (error: any) => {
                console.error('Failed to load units:', error);
                this.error = 'Erreur lors du chargement des logements';
                this.loading = false;
                this.cdr.markForCheck();
            }
        });
    }

    getRoleForCurrentUser(unit: Unit): string {
        // Find current user's role in the unit
        // This would need the current user ID - for now, just show all roles
        return unit.members?.map((m: any) => m.role).join(', ') || '';
    }

    viewUnit(unitId: string): void {
        window.scrollTo({ top: 0, behavior: 'smooth' });
        this.router.navigate(['/hub/settings/units', unitId]);
    }

    getRoleLabel(role: string): string {
        if (this.roleLabelsCache.has(role)) {
            return this.roleLabelsCache.get(role)!;
        }
        
        let label: string;
        if (role === 'ADMIN') {
            label = this.translate.instant('units.role.admin');
        } else {
            label = this.translate.instant('units.role.member');
        }
        
        this.roleLabelsCache.set(role, label);
        return label;
    }

    isPrimaryUnit(unit: UnitWithMetadata): boolean {
        return unit.isPrimary === true;
    }

    getRoleLabelForMember(unit: UnitWithMetadata, role: string | null | undefined): string {
        if (!role) return '';
        return unit.roleLabels?.get(role) || this.getRoleLabel(role);
    }

    trackByUnitId(index: number, unit: UnitWithMetadata): string {
        return unit.id || '';
    }

    trackByMemberId(index: number, member: any): string {
        return member.userId || '';
    }
}

