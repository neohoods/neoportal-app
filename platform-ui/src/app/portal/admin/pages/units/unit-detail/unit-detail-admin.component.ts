import { CommonModule, DatePipe } from '@angular/common';
import { Component, OnInit, signal, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiAutoColorPipe, TuiInitialsPipe, TuiButton, TuiDialogService, TuiIcon, TuiLoader, TuiNotification } from '@taiga-ui/core';
import { type TuiStringMatcher } from '@taiga-ui/cdk';
import { TuiAvatar } from '@taiga-ui/kit';
import { TUI_CONFIRM, TuiConfirmData } from '@taiga-ui/kit';
import { TuiComboBox, TuiDataListWrapper, TuiFilterByInputPipe, TuiSelect, TuiChevron } from '@taiga-ui/kit';
import { TuiTextfield, TuiTextfieldDropdownDirective, TuiLabel } from '@taiga-ui/core';
import { FormsModule } from '@angular/forms';
import { UNITS_SERVICE_TOKEN, USERS_SERVICE_TOKEN } from '../../../admin.providers';
import { UnitsService } from '../../../services/units.service';
import { UsersService } from '../../../services/users.service';
import { Unit } from '../../../../../api-client/model/unit';
import { UnitMember } from '../../../../../api-client/model/unitMember';
import { UIUser } from '../../../../../models/UIUser';
import { UnitsAdminApiService } from '../../../../../api-client/api/unitsAdminApi.service';
import { InviteUserRequest } from '../../../../../api-client/model/inviteUserRequest';
import { AUTH_SERVICE_TOKEN } from '../../../../../global.provider';
import { AuthService } from '../../../../../services/auth.service';

@Component({
  selector: 'app-unit-detail-admin',
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
    TuiComboBox,
    TuiDataListWrapper,
    TuiFilterByInputPipe,
    TuiSelect,
    TuiTextfield,
    TuiTextfieldDropdownDirective,
    TuiChevron,
    TuiLabel,
    TuiAvatar,
    TuiInitialsPipe,
    TuiAutoColorPipe
  ],
  templateUrl: './unit-detail-admin.component.html',
  styleUrl: './unit-detail-admin.component.scss'
})
export class UnitDetailAdminComponent implements OnInit {
  private unitsService = inject(UNITS_SERVICE_TOKEN);
  private usersService = inject(USERS_SERVICE_TOKEN);
  private unitsAdminApi = inject(UnitsAdminApiService);
  private authService = inject(AUTH_SERVICE_TOKEN);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private translate = inject(TranslateService);
  private alerts = inject(TuiAlertService);
  private dialogService = inject(TuiDialogService);

  unit = signal<Unit | null>(null);
  loading = signal(true);
  error: string | null = null;
  
  // Member management
  availableUsers = signal<UIUser[]>([]);
  selectedUser = signal<UIUser | null>(null);
  addingMember = signal(false);

  constructor() {}

  ngOnInit(): void {
    const unitId = this.route.snapshot.paramMap.get('id');
    if (unitId) {
      this.loadUnit(unitId);
      this.loadUsers();
    } else {
      this.router.navigate(['/admin/units']);
    }
  }

  loadUnit(unitId: string): void {
    this.loading.set(true);
    this.error = null;

    this.unitsService.getUnit(unitId).subscribe({
      next: (unit: Unit) => {
        this.unit.set(unit);
        this.loading.set(false);
      },
      error: (error: any) => {
        console.error('Failed to load unit:', error);
        this.error = 'Erreur lors du chargement du logement';
        this.loading.set(false);
      }
    });
  }

  loadUsers(): void {
    this.usersService.getUsers().subscribe({
      next: (users: UIUser[]) => {
        this.availableUsers.set(users);
      },
      error: (error: any) => {
        console.error('Failed to load users:', error);
      }
    });
  }

  onBack(): void {
    this.router.navigate(['/admin/units']);
  }

  getRoleLabel(role: string): string {
    return role === 'ADMIN' ? 'Administrateur' : 'Membre';
  }

  stringifyUser = (user: UIUser | null): string => {
    if (!user) return '';
    return `${user.firstName} ${user.lastName} (${user.email})`;
  };

  readonly userMatcher: TuiStringMatcher<UIUser> = (item, query) => {
    const fullName = `${item.firstName} ${item.lastName}`.toLowerCase();
    const email = item.email?.toLowerCase() || '';
    const searchQuery = query.toLowerCase();
    return fullName.includes(searchQuery) || email.includes(searchQuery);
  };

  addMember(): void {
    const unit = this.unit();
    const selectedUser = this.selectedUser();
    
    if (!unit || !selectedUser) {
      return;
    }

    // Check if user is already a member
    if (unit.members?.some(m => m.userId === selectedUser.id)) {
      this.alerts.open(this.translate.instant('units.memberAlreadyExists')).subscribe();
      return;
    }

    this.addingMember.set(true);
    const inviteRequest: InviteUserRequest = {
      userId: selectedUser.id
    };

    this.unitsAdminApi.addMember(unit.id!, inviteRequest).subscribe({
      next: () => {
        this.alerts.open(this.translate.instant('units.memberAdded')).subscribe();
        this.selectedUser.set(null);
        this.loadUnit(unit.id!);
        this.addingMember.set(false);
      },
      error: (error: any) => {
        console.error('Failed to add member:', error);
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

    this.dialogService
      .open<boolean>(TUI_CONFIRM, {
        label: this.translate.instant('units.removeMember', { name: memberName }),
        size: 'm',
        data,
      })
      .subscribe((response) => {
        if (response) {
          this.unitsAdminApi.revokeAdminMember(unit.id!, member.userId).subscribe({
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

    this.unitsAdminApi.promoteAdminMember(unit.id!, member.userId).subscribe({
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

    this.unitsAdminApi.demoteAdminMember(unit.id!, member.userId).subscribe({
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

  getAvailableUsersForSelect(): UIUser[] {
    const unit = this.unit();
    if (!unit || !unit.members) {
      return this.availableUsers();
    }

    const memberUserIds = new Set(unit.members.map(m => m.userId));
    return this.availableUsers().filter(user => !memberUserIds.has(user.id));
  }

  isCurrentUser(member: UnitMember): boolean {
    const currentUser = this.authService.getCurrentUserInfo().user;
    return member.userId === currentUser.id;
  }
}

