import { CommonModule, DatePipe } from '@angular/common';
import { Component, Inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiAutoColorPipe, TuiInitialsPipe, TuiButton, TuiDialogService, TuiIcon, TuiLoader, TuiNotification, TuiTextfield, TuiLabel } from '@taiga-ui/core';
import { TuiAvatar } from '@taiga-ui/kit';
import { TUI_CONFIRM, TuiConfirmData } from '@taiga-ui/kit';
import { UnitsHubApiService } from '../../../../../../api-client/api/unitsHubApi.service';
import { Unit } from '../../../../../../api-client/model/unit';
import { UnitMember } from '../../../../../../api-client/model/unitMember';
import { InviteUserRequest } from '../../../../../../api-client/model/inviteUserRequest';
import { AUTH_SERVICE_TOKEN } from '../../../../../../global.provider';
import { AuthService } from '../../../../../../services/auth.service';

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
    TuiAutoColorPipe
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

  constructor(
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
    private unitsApiService: UnitsHubApiService,
    private route: ActivatedRoute,
    private router: Router,
    private translate: TranslateService,
    private alerts: TuiAlertService,
    private dialogs: TuiDialogService
  ) {}

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
      },
      error: (error: any) => {
        console.error('Failed to load unit:', error);
        this.error = 'Erreur lors du chargement du logement';
        this.loading.set(false);
      }
    });
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
}

