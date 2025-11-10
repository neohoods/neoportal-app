import { CommonModule, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiAutoColorPipe, TuiButton, TuiDialogContext, TuiDialogService, TuiIcon, TuiInitialsPipe, TuiNotification } from '@taiga-ui/core';
import { TuiAvatar, TuiChip } from '@taiga-ui/kit';
import { UnitsHubApiService } from '../../../../../api-client/api/unitsHubApi.service';
import { Unit } from '../../../../../api-client/model/unit';
import { AUTH_SERVICE_TOKEN } from '../../../../../global.provider';
import { UNITS_SERVICE_TOKEN } from '../../../hub.provider';
import { JoinUnitDialogComponent } from './join-unit-dialog.component';

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
        TuiChip,
        TuiButton,
        JoinUnitDialogComponent
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
    private currentUserId: string | null = null;
    private roleLabelsCache = new Map<string, string>();
    pendingRequests = new Map<string, any>(); // unitId -> request

    @ViewChild('joinUnitDialogTemplate', { static: true })
    private joinUnitDialogTemplate!: TemplateRef<TuiDialogContext>;

    private unitsApiService = inject(UnitsHubApiService);
    private unitsService = inject(UNITS_SERVICE_TOKEN);
    private router = inject(Router);
    private translate = inject(TranslateService);
    private authService = inject(AUTH_SERVICE_TOKEN);
    private alerts = inject(TuiAlertService);
    private cdr = inject(ChangeDetectorRef);
    private dialogs = inject(TuiDialogService);

    ngOnInit(): void {
        // Cache primary unit ID once
        const currentUser = this.authService.getCurrentUserInfo()?.user;
        this.primaryUnitId = currentUser?.primaryUnitId || null;
        this.currentUserId = currentUser?.id || null;

        // Pre-cache role labels
        this.roleLabelsCache.set('ADMIN', this.translate.instant('units.role.admin'));
        this.roleLabelsCache.set('MEMBER', this.translate.instant('units.role.member'));

        this.loadUnits();
    }

    loadUnits(): void {
        this.loading = true;
        this.error = null;
        this.cdr.markForCheck();

        // Load both user's units and units with pending requests
        this.unitsApiService.getUnits().subscribe({
            next: (units: Unit[]) => {
                // Load pending requests to get units that user requested to join
                this.unitsService.getMyJoinRequests().subscribe({
                    next: (requests: any[]) => {
                        // Update pending requests map
                        this.pendingRequests.clear();
                        requests.forEach(request => {
                            if (request.unitId) {
                                this.pendingRequests.set(request.unitId, request);
                            }
                        });

                        // Pre-process units to add metadata
                        const userUnits = units.map(unit => {
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

                        // Load units for pending requests that are not in user's units
                        const pendingUnitsToLoad: Promise<UnitWithMetadata>[] = requests
                            .filter(r => r.unitId && !userUnits.some(u => u.id === r.unitId))
                            .map(request => {
                                return new Promise<UnitWithMetadata>((resolve, reject) => {
                                    // Get unit from directory
                                    this.unitsService.getUnitsDirectory(0, 1000).subscribe({
                                        next: (paginated: any) => {
                                            const unit = paginated.content?.find((u: Unit) => u.id === request.unitId);
                                            if (unit) {
                                                const unitWithMetadata: UnitWithMetadata = {
                                                    ...unit,
                                                    isPrimary: false,
                                                    roleLabels: new Map<string, string>()
                                                };
                                                resolve(unitWithMetadata);
                                            } else {
                                                reject(new Error('Unit not found'));
                                            }
                                        },
                                        error: reject
                                    });
                                });
                            });

                        if (pendingUnitsToLoad.length > 0) {
                            Promise.all(pendingUnitsToLoad).then(pendingUnits => {
                                // Combine: pending units first, then user units
                                this.units = [...pendingUnits, ...userUnits];
                                this.loading = false;
                                this.cdr.markForCheck();
                            }).catch(error => {
                                console.error('Failed to load pending units:', error);
                                // Fallback to just user units
                                this.units = userUnits;
                                this.loading = false;
                                this.cdr.markForCheck();
                            });
                        } else {
                            // No pending units to load, just use user units
                            this.units = userUnits;
                            this.loading = false;
                            this.cdr.markForCheck();
                        }
                    },
                    error: (error: any) => {
                        console.error('Failed to load pending requests:', error);
                        // Continue with just user units
                        const userUnits = units.map(unit => {
                            const unitWithMetadata: UnitWithMetadata = {
                                ...unit,
                                isPrimary: this.primaryUnitId === unit.id,
                                roleLabels: new Map<string, string>()
                            };

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
                        this.units = userUnits;
                        this.loading = false;
                        this.cdr.markForCheck();
                    }
                });
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


    openJoinUnitDialog(): void {
        this.dialogs
            .open(this.joinUnitDialogTemplate, {
                label: this.translate.instant('settings.units.joinRequests.joinUnit'),
                size: 'm',
                data: {}
            })
            .subscribe({
                next: () => {
                    // Reload units after dialog closes in case user was added to a unit
                    this.loadUnits();
                }
            });
    }

    hasPendingRequest(unitId: string | null | undefined): boolean {
        if (!unitId) return false;
        return this.pendingRequests.has(unitId);
    }
}

