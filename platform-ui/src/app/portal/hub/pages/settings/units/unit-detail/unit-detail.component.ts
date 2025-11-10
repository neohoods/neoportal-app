import { CommonModule, DatePipe } from '@angular/common';
import { Component, Inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiAutoColorPipe, TuiButton, TuiDialogService, TuiIcon, TuiInitialsPipe, TuiLabel, TuiLoader, TuiNotification, TuiTextfield, TuiTextfieldDropdownDirective } from '@taiga-ui/core';
import { TUI_CONFIRM, TuiAvatar, TuiChevron, TuiChip, TuiConfirmData, TuiDataListWrapper } from '@taiga-ui/kit';
import { TuiInputModule, TuiSelectModule } from '@taiga-ui/legacy';
import { UnitsAdminApiService } from '../../../../../../api-client/api/unitsAdminApi.service';
import { UnitsHubApiService } from '../../../../../../api-client/api/unitsHubApi.service';
import { InviteUserRequest } from '../../../../../../api-client/model/inviteUserRequest';
import { Unit } from '../../../../../../api-client/model/unit';
import { UnitMember } from '../../../../../../api-client/model/unitMember';
import { UpdateMemberResidenceRoleRequest } from '../../../../../../api-client/model/updateMemberResidenceRoleRequest';
import { AUTH_SERVICE_TOKEN } from '../../../../../../global.provider';
import { AuthService } from '../../../../../../services/auth.service';
import { UNITS_SERVICE_TOKEN } from '../../../../hub.provider';
import { UnitsService } from '../../../../services/units.service';

// Temporary types until OpenAPI models are regenerated
type UnitMemberResidenceRole = 'PROPRIETAIRE' | 'BAILLEUR' | 'MANAGER' | 'TENANT' | null;

@Component({
  selector: 'app-unit-detail',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    RouterLink,
    FormsModule,
    TranslateModule,
    TuiButton,
    TuiIcon,
    TuiLoader,
    TuiNotification,
    TuiTextfield,
    TuiLabel,
    TuiAvatar,
    TuiInitialsPipe,
    TuiAutoColorPipe,
    TuiSelectModule,
    TuiDataListWrapper,
    TuiInputModule,
    TuiChip,
    TuiTextfieldDropdownDirective,
    TuiChevron
  ],
  templateUrl: './unit-detail.component.html',
  styleUrl: './unit-detail.component.scss'
})
export class UnitDetailComponent implements OnInit {
  unit = signal<Unit | null>(null);
  loading = signal(true);
  error: string | null = null;

  // Member management
  inviteEmail = signal<string>('');
  addingMember = signal(false);
  isAdmin = signal(false);

  // Related parking/garages
  relatedParkingGarages = signal<Unit[]>([]);
  loadingRelated = signal(false);

  // Join requests
  joinRequests = signal<any[]>([]);
  loadingJoinRequests = signal(false);
  hasPendingRequest = signal(false);

  // Editing residence role
  editingResidenceRoleFor = signal<string | null>(null);

  // Residence role options
  residenceRoleOptions = [
    { value: null, label: 'units.residenceRoles.none' },
    { value: 'PROPRIETAIRE' as UnitMemberResidenceRole, label: 'units.residenceRoles.PROPRIETAIRE' },
    { value: 'BAILLEUR' as UnitMemberResidenceRole, label: 'units.residenceRoles.BAILLEUR' },
    { value: 'MANAGER' as UnitMemberResidenceRole, label: 'units.residenceRoles.MANAGER' },
    { value: 'TENANT' as UnitMemberResidenceRole, label: 'units.residenceRoles.TENANT' },
  ];

  stringifyResidenceRole = (item: any): string => {
    if (!item || !item.label) return '';
    return this.translate.instant(item.label);
  };

  constructor(
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
    @Inject(UNITS_SERVICE_TOKEN) private unitsService: UnitsService,
    private unitsApiService: UnitsHubApiService,
    private unitsAdminApiService: UnitsAdminApiService,
    private route: ActivatedRoute,
    private router: Router,
    private translate: TranslateService,
    private alerts: TuiAlertService,
    private dialogs: TuiDialogService
  ) { }

  ngOnInit(): void {
    const unitId = this.route.snapshot.paramMap.get('id');
    if (unitId) {
      this.loadUnit(unitId);
    } else {
      this.router.navigate(['/hub/settings/units']);
    }
  }

  loadUnit(unitId: string): void {
    this.loading.set(true);
    this.error = null;

    // Check if user has a pending request for this unit
    this.unitsService.getMyJoinRequests().subscribe({
      next: (requests: any[]) => {
        const hasPending = requests.some(r => r.unitId === unitId);
        this.hasPendingRequest.set(hasPending);
      },
      error: () => {
        this.hasPendingRequest.set(false);
      }
    });

    this.unitsApiService.getUnit(unitId).subscribe({
      next: (unit: Unit) => {
        this.unit.set(unit);
        // Check if current user is admin of this unit
        const currentUser = this.authService.getCurrentUserInfo().user;
        const currentUserIsAdmin = unit.members?.some(m =>
          m.userId === currentUser.id && m.role === 'ADMIN'
        ) || false;
        this.isAdmin.set(currentUserIsAdmin);
        this.loading.set(false);

        // Load related parking/garages if this is a FLAT and user is admin
        // Check if user is PROPRIETAIRE of this unit
        const userMember = unit.members?.find(m => m.userId === currentUser.id);
        const isProprietaire = userMember?.residenceRole?.toString() === 'PROPRIETAIRE';
        if (unit.type === 'FLAT' && (currentUserIsAdmin || isProprietaire)) {
          this.loadRelatedParkingGarages(currentUser.id);
        }

        // Load join requests if user is admin
        if (currentUserIsAdmin) {
          this.loadJoinRequests(unitId);
        }
      },
      error: (error: any) => {
        console.error('Failed to load unit:', error);
        this.error = 'Erreur lors du chargement du logement';
        this.loading.set(false);
      }
    });
  }

  loadRelatedParkingGarages(userId: string): void {
    this.loadingRelated.set(true);
    this.unitsService.getRelatedParkingGarages(userId).subscribe({
      next: (units: Unit[]) => {
        this.relatedParkingGarages.set(units);
        this.loadingRelated.set(false);
      },
      error: (error: any) => {
        console.error('Failed to load related parking/garages:', error);
        this.loadingRelated.set(false);
      }
    });
  }

  updateMemberResidenceRole(member: UnitMember, residenceRole: UnitMemberResidenceRole): void {
    const unit = this.unit();
    if (!unit) return;

    // Extract value if it's an object, otherwise use the value directly
    let roleValue: string | null = null;
    if (residenceRole !== null && residenceRole !== undefined) {
      if (typeof residenceRole === 'string') {
        roleValue = residenceRole;
      } else if ((residenceRole as any).value !== undefined) {
        roleValue = (residenceRole as any).value;
      } else {
        roleValue = String(residenceRole);
      }
    }

    const request: UpdateMemberResidenceRoleRequest = {
      residenceRole: roleValue as any,
    };

    // Use admin API for updating residence role
    this.unitsAdminApiService.updateMemberResidenceRole(unit.id!, member.userId, request).subscribe({
      next: () => {
        this.alerts.open(this.translate.instant('units.residenceRoleUpdated')).subscribe();
        this.loadUnit(unit.id!);
      },
      error: (error: any) => {
        console.error('Failed to update residence role:', error);
        this.alerts.open(this.translate.instant('units.residenceRoleError')).subscribe();
      }
    });
  }

  getResidenceRoleLabel(role: any): string {
    if (!role) return '';
    // Handle both string and object (enum) cases
    let roleValue: string;
    if (typeof role === 'string') {
      roleValue = role;
    } else if (role?.value) {
      roleValue = role.value;
    } else if (role?.toString && role.toString() !== '[object Object]') {
      roleValue = role.toString();
    } else {
      roleValue = Object.values(role)[0] as string || '';
    }
    if (!roleValue || roleValue === '[object Object]') return '';
    const key = `units.residenceRoles.${roleValue}`;
    return this.translate.instant(key);
  }

  getResidenceRoleForMember(member: UnitMember): string | null {
    if (!member.residenceRole) return null;
    const role = member.residenceRole as any;
    const roleValue = typeof role === 'string'
      ? role
      : (role?.value || role?.toString() || null);
    return roleValue;
  }

  startEditingResidenceRole(member: UnitMember): void {
    this.editingResidenceRoleFor.set(member.userId);
  }

  cancelEditingResidenceRole(): void {
    this.editingResidenceRoleFor.set(null);
  }

  isEditingResidenceRole(member: UnitMember): boolean {
    return this.editingResidenceRoleFor() === member.userId;
  }

  getTypeLabel(type: string | undefined): string {
    if (!type) return '';
    const key = `units.types.${type}`;
    return this.translate.instant(key);
  }


  onBack(): void {
    this.router.navigate(['/hub/settings/units']);
  }

  getRoleLabel(role: string): string {
    return role === 'ADMIN' ? 'Administrateur' : 'Membre';
  }

  addMember(): void {
    const unit = this.unit();
    const email = this.inviteEmail().trim();

    if (!unit || !email) {
      return;
    }

    // Basic email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      this.alerts.open(this.translate.instant('units.invalidEmail')).subscribe();
      return;
    }

    this.addingMember.set(true);
    const inviteRequest: InviteUserRequest = {
      email: email
    };

    this.unitsApiService.inviteUser(unit.id!, inviteRequest).subscribe({
      next: () => {
        this.alerts.open(this.translate.instant('units.memberAdded')).subscribe();
        this.inviteEmail.set('');
        this.loadUnit(unit.id!);
        this.addingMember.set(false);
      },
      error: (error: any) => {
        console.error('Failed to invite member:', error);
        this.alerts.open(this.translate.instant('units.addMemberError')).subscribe();
        this.addingMember.set(false);
      }
    });
  }

  removeMember(member: UnitMember): void {
    const unit = this.unit();
    if (!unit) return;

    // Prevent user from removing themselves
    const currentUser = this.authService.getCurrentUserInfo().user;
    if (member.userId === currentUser.id) {
      this.alerts.open(this.translate.instant('units.cannotRemoveYourself')).subscribe();
      return;
    }

    const memberName = (member.user?.firstName || '') + ' ' + (member.user?.lastName || '');
    const data: TuiConfirmData = {
      content: this.translate.instant('units.confirmRemoveMember', { name: memberName }),
      yes: this.translate.instant('common.yes'),
      no: this.translate.instant('common.no'),
    };

    this.dialogs
      .open<boolean>(TUI_CONFIRM, {
        label: this.translate.instant('units.removeMember', { name: memberName }),
        size: 'm',
        data,
      })
      .subscribe((response) => {
        if (response) {
          this.unitsApiService.revokeMember(unit.id!, member.userId).subscribe({
            next: () => {
              this.alerts.open(this.translate.instant('units.memberRemoved')).subscribe();
              this.loadUnit(unit.id!);
            },
            error: (error: any) => {
              console.error('Failed to remove member:', error);
              this.alerts.open(this.translate.instant('units.removeMemberError')).subscribe();
            }
          });
        }
      });
  }

  promoteToAdmin(member: UnitMember): void {
    const unit = this.unit();
    if (!unit) return;

    this.unitsApiService.promoteAdmin(unit.id!, member.userId).subscribe({
      next: () => {
        this.alerts.open(this.translate.instant('units.memberPromoted')).subscribe();
        this.loadUnit(unit.id!);
      },
      error: (error: any) => {
        console.error('Failed to promote member:', error);
        this.alerts.open(this.translate.instant('units.promoteError')).subscribe();
      }
    });
  }

  demoteFromAdmin(member: UnitMember): void {
    const unit = this.unit();
    if (!unit) return;

    // Prevent user from demoting themselves
    const currentUser = this.authService.getCurrentUserInfo().user;
    if (member.userId === currentUser.id) {
      this.alerts.open(this.translate.instant('units.cannotDemoteYourself')).subscribe();
      return;
    }

    this.unitsApiService.demoteAdmin(unit.id!, member.userId).subscribe({
      next: () => {
        this.alerts.open(this.translate.instant('units.memberDemoted')).subscribe();
        this.loadUnit(unit.id!);
      },
      error: (error: any) => {
        console.error('Failed to demote member:', error);
        this.alerts.open(this.translate.instant('units.demoteError')).subscribe();
      }
    });
  }

  isCurrentUser(member: UnitMember): boolean {
    const currentUser = this.authService.getCurrentUserInfo().user;
    return member.userId === currentUser.id;
  }

  loadJoinRequests(unitId: string): void {
    this.loadingJoinRequests.set(true);
    this.unitsService.getJoinRequests(unitId).subscribe({
      next: (requests: any[]) => {
        this.joinRequests.set(requests);
        this.loadingJoinRequests.set(false);
      },
      error: (error: any) => {
        console.error('Failed to load join requests:', error);
        this.loadingJoinRequests.set(false);
      }
    });
  }

  approveJoinRequest(requestId: string): void {
    this.unitsService.approveJoinRequest(requestId).subscribe({
      next: () => {
        this.alerts.open(
          this.translate.instant('settings.units.joinRequests.requestApproved'),
          { appearance: 'positive' }
        ).subscribe();
        const unit = this.unit();
        if (unit) {
          this.loadUnit(unit.id!);
          this.loadJoinRequests(unit.id!);
        }
      },
      error: (error: any) => {
        console.error('Failed to approve join request:', error);
        this.alerts.open(
          this.translate.instant('settings.units.joinRequests.requestApproved') + ': ' + (error.message || 'Erreur'),
          { appearance: 'negative' }
        ).subscribe();
      }
    });
  }

  rejectJoinRequest(requestId: string): void {
    this.unitsService.rejectJoinRequest(requestId).subscribe({
      next: () => {
        this.alerts.open(
          this.translate.instant('settings.units.joinRequests.requestRejected'),
          { appearance: 'positive' }
        ).subscribe();
        const unit = this.unit();
        if (unit) {
          this.loadJoinRequests(unit.id!);
        }
      },
      error: (error: any) => {
        console.error('Failed to reject join request:', error);
        this.alerts.open(
          this.translate.instant('settings.units.joinRequests.requestRejected') + ': ' + (error.message || 'Erreur'),
          { appearance: 'negative' }
        ).subscribe();
      }
    });
  }
}

