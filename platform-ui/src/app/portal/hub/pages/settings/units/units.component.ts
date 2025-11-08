import { CommonModule, DatePipe } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAutoColorPipe, TuiIcon, TuiInitialsPipe, TuiNotification } from '@taiga-ui/core';
import { TuiAvatar, TuiChip } from '@taiga-ui/kit';
import { UnitsHubApiService } from '../../../../../api-client/api/unitsHubApi.service';
import { Unit } from '../../../../../api-client/model/unit';
import { AUTH_SERVICE_TOKEN } from '../../../../../global.provider';
import { AuthService } from '../../../../../services/auth.service';

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
    styleUrl: './units.component.scss'
})
export class UnitsComponent implements OnInit {
    units: Unit[] = [];
    loading = true;
    error: string | null = null;

    constructor(
        private unitsApiService: UnitsHubApiService,
        private router: Router,
        private translate: TranslateService,
        @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService
    ) { }

    ngOnInit(): void {
        this.loadUnits();
    }

    loadUnits(): void {
        this.loading = true;
        this.error = null;

        this.unitsApiService.getUnits().subscribe({
            next: (units: Unit[]) => {
                this.units = units;
                this.loading = false;
            },
            error: (error: any) => {
                console.error('Failed to load units:', error);
                this.error = 'Erreur lors du chargement des logements';
                this.loading = false;
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
        if (role === 'ADMIN') {
            return this.translate.instant('units.role.admin');
        }
        return this.translate.instant('units.role.member');
    }

    isPrimaryUnit(unit: Unit): boolean {
        const currentUser = this.authService.getCurrentUserInfo()?.user;
        if (!currentUser || !currentUser.primaryUnitId) {
            return false;
        }
        return currentUser.primaryUnitId === unit.id;
    }
}

