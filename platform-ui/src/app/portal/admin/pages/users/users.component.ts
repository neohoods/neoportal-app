import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import {
  FormControl,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
} from '@angular/forms';
import { RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiResponsiveDialogService } from '@taiga-ui/addon-mobile';
import { TuiTable } from '@taiga-ui/addon-table';
import { TuiAutoFocus } from '@taiga-ui/cdk';
import {
  TuiAlertService,
  TuiAutoColorPipe,
  TuiButton,
  TuiDialog,
  TuiDropdown,
  TuiHint,
  TuiIcon,
  TuiInitialsPipe
} from '@taiga-ui/core';
import {
  TUI_CONFIRM,
  TuiAvatar,
  TuiChip,
  TuiConfirmData
} from '@taiga-ui/kit';
import { TuiInputModule } from '@taiga-ui/legacy';
import { map } from 'rxjs';
import { UIUser, UIUserType } from '../../../../models/UIUser';
import { USERS_SERVICE_TOKEN } from '../../admin.providers';
import {
  Column,
  TosTableComponent,
} from '../../components/tos-table/tos-table.component';
import { UsersService } from '../../services/users.service';



@Component({
  standalone: true,
  imports: [
    TuiAutoFocus,
    TuiButton,
    TuiDialog,
    TuiHint,
    TuiInputModule,
    ReactiveFormsModule,
    TuiDialog,
    TuiButton,
    TuiAutoColorPipe,
    TuiInitialsPipe,
    TuiAvatar,
    RouterModule,
    FormsModule,
    TuiDropdown,
    TuiTable,
    TuiIcon,
    TuiChip,
    TosTableComponent,
    TranslateModule,
    DatePipe
  ],
  selector: 'app-users',
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersComponent {
  // Default Table Size
  protected users: UIUser[] = [];
  currentUser: UIUser | undefined;

  // Expose enum for template
  UIUserType = UIUserType;

  // Available Columns for Display
  columns: Column[] = [];
  passwordForm = new FormGroup({
    userPasswordControl: new FormControl(''),
  });

  protected openPasswordDialog = false;
  isMobile: boolean = false;

  constructor(
    private breakpointObserver: BreakpointObserver,
    private dialogs: TuiResponsiveDialogService,
    private alerts: TuiAlertService,
    @Inject(USERS_SERVICE_TOKEN) private usersService: UsersService,
    private translate: TranslateService
  ) {
    this.columns = [
      {
        key: 'username',
        label: this.translate.instant('users.columns.username'),
        custom: true,
        visible: true,
        sortable: true,
        size: 'm',
      },
      {
        key: 'email',
        label: this.translate.instant('users.columns.email'),
        visible: true,
        sortable: true,
        size: 'm',
      },
      {
        key: 'flatNumber',
        label: this.translate.instant('users.columns.flatNumber'),
        visible: true,
        sortable: true,
        size: 'm',
      },
      {
        key: 'type',
        label: this.translate.instant('users.columns.type'),
        visible: true,
        sortable: true,
        size: 's',
        custom: true,
      },
      {
        key: 'createdAt',
        label: this.translate.instant('users.columns.createdAt'),
        visible: true,
        sortable: true,
        custom: true,
        size: 'm',
      },
      {
        key: 'streetAddress',
        label: this.translate.instant('users.columns.address'),
        visible: false,
        sortable: false,
        size: 'l',
      }
    ];
    this.usersService.getUsers().subscribe((users) => (this.users = users));

    this.breakpointObserver
      .observe([Breakpoints.Handset])
      .subscribe((result) => {
        this.isMobile = result.matches;
      });
  }

  deleteUser(user: UIUser): void {
    const data: TuiConfirmData = {
      content: this.translate.instant('users.confirmDeleteContent'),
      yes: this.translate.instant('users.confirmDeleteYes'),
      no: this.translate.instant('users.confirmDeleteNo'),
    };

    this.dialogs
      .open<boolean>(TUI_CONFIRM, {
        label: this.translate.instant('users.confirmDeleteLabel', { username: user.username }),
        size: 'm',
        data,
      })
      .subscribe(response => {
        if (response) {
          this.alerts.open(
            this.translate.instant('users.deleteSuccess', { username: user.username }),
            { appearance: 'positive' },
          ).subscribe();
        }
      });
  }

  disableUser(user: UIUser): void {
    const data: TuiConfirmData = {
      content: this.translate.instant('users.confirmDisableContent'),
      yes: this.translate.instant('users.confirmDisableYes'),
      no: this.translate.instant('users.confirmDisableNo'),
    };

    this.dialogs
      .open<boolean>(TUI_CONFIRM, {
        label: this.translate.instant('users.confirmDisableLabel', { username: user.username }),
        size: 'm',
        data,
      })
      .subscribe((response) => {
        if (response) {
          this.alerts.open(
            this.translate.instant('users.disableSuccess', { username: user.username }),
            { appearance: 'positive' },
          ).subscribe();
        }
      });
  }

  protected setPassword(user: UIUser): void {
    this.openPasswordDialog = true;
    this.currentUser = user;
  }

  getDataFunction = (
    searchText?: string,
    sortBy?: string,
    sortOrder?: 'asc' | 'desc',
    page?: number,
    pageSize?: number
  ) => {
    return this.usersService.getUsers().pipe(
      map((users) => {
        // Filter users based on search text
        let filteredUsers = users;
        if (searchText && searchText.trim()) {
          const searchLower = searchText.toLowerCase().trim();
          filteredUsers = users.filter(user =>
            user.username?.toLowerCase().includes(searchLower) ||
            user.email?.toLowerCase().includes(searchLower) ||
            user.flatNumber?.toLowerCase().includes(searchLower) ||
            user.streetAddress?.toLowerCase().includes(searchLower) ||
            user.type?.toLowerCase().includes(searchLower) ||
            user.createdAt?.toLowerCase().includes(searchLower)
          );
        }

        // Sort users if sortBy is specified
        if (sortBy) {
          filteredUsers = [...filteredUsers].sort((a, b) => {
            const aValue = a[sortBy as keyof UIUser];
            const bValue = b[sortBy as keyof UIUser];

            // Handle undefined values
            if (aValue === undefined && bValue === undefined) return 0;
            if (aValue === undefined) return 1;
            if (bValue === undefined) return -1;

            if (aValue === bValue) return 0;

            // Special handling for createdAt (date sorting)
            if (sortBy === 'createdAt') {
              const dateA = new Date(aValue as string).getTime();
              const dateB = new Date(bValue as string).getTime();
              const comparison = dateA - dateB;
              return sortOrder === 'desc' ? -comparison : comparison;
            }

            const comparison = aValue < bValue ? -1 : 1;
            return sortOrder === 'desc' ? -comparison : comparison;
          });
        }

        // Calculate pagination
        const totalItems = filteredUsers.length;
        const itemsPerPage = pageSize || 12;
        const totalPages = Math.ceil(totalItems / itemsPerPage);
        const currentPage = page || 1;
        const startIndex = (currentPage - 1) * itemsPerPage;
        const endIndex = startIndex + itemsPerPage;
        const paginatedUsers = filteredUsers.slice(startIndex, endIndex);

        return {
          totalPages,
          totalItems,
          currentPage,
          itemsPerPage,
          items: paginatedUsers
        }
      })
    );
  }

  setUserPassword() {
    if (this.passwordForm.valid) {
      const newPassword = this.passwordForm.get('userPasswordControl')?.value;
      if (typeof newPassword === 'string' && this.currentUser?.id) {
        this.usersService
          .setUserPassword(this.currentUser.id, newPassword)
          .subscribe(() => {
            this.openPasswordDialog = false;
            this.alerts
              .open(
                this.translate.instant('users.passwordSetSuccess', { username: this.currentUser?.username }),
                { appearance: 'positive' },
              )
              .subscribe();
          });
      }
    }
  }


}
