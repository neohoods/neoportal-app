import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { type TuiStringMatcher } from '@taiga-ui/cdk';
import {
  TuiAutoColorPipe,
  TuiButton,
  TuiIcon,
  TuiInitialsPipe,
  TuiLabel,
  TuiLink,
  TuiLoader,
  TuiTextfield,
  TuiTitle,
} from '@taiga-ui/core';
import { TuiExpand } from '@taiga-ui/experimental';
import {
  TuiAvatar,
  TuiChevron,
  TuiChip,
  TuiComboBox,
  TuiDataListWrapper,
  TuiFilterByInputPipe,
  TuiPagination,
  TuiSwitch,
} from '@taiga-ui/kit';
import { TuiCard, TuiHeader } from '@taiga-ui/layout';
import { TuiInputModule, TuiSelectModule } from '@taiga-ui/legacy';
import { UNITS_SERVICE_TOKEN } from '../../hub.provider';
import { UnitsService } from '../../services/units.service';
// Types will be generated from OpenAPI - using temporary types for now
// import { Unit } from '../../../../api-client/model/unit';
// import { PaginatedUnits } from '../../../../api-client/model/paginatedUnits';
// import { UnitType } from '../../../../api-client/model/unitType';
import { AUTH_SERVICE_TOKEN } from '../../../../global.provider';
import { UIUser } from '../../../../models/UIUser';
import { AuthService } from '../../../../services/auth.service';
import { UsersService } from '../../../admin/services/users.service';
import { USERS_SERVICE_TOKEN } from '../../hub.provider';

// Temporary types until OpenAPI models are regenerated
type Unit = any;
type PaginatedUnits = any;
type UnitType = 'FLAT' | 'GARAGE' | 'PARKING' | null;

@Component({
  selector: 'app-directory',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    TuiLabel,
    TuiLoader,
    TuiTextfield,
    TuiSelectModule,
    TuiDataListWrapper,
    TuiInputModule,
    TuiPagination,
    TuiComboBox,
    TuiFilterByInputPipe,
    TuiChip,
    TuiIcon,
    TuiAvatar,
    TuiAutoColorPipe,
    TuiInitialsPipe,
    TuiCard,
    TuiHeader,
    TuiTitle,
    TuiButton,
    TuiLink,
    TuiChevron,
    TuiExpand,
    TuiSwitch,
  ],
  templateUrl: './directory.component.html',
  styleUrl: './directory.component.scss',
})
export class DirectoryComponent implements OnInit {
  units = signal<Unit[]>([]);
  loading = signal(true);
  totalPages = signal(0);
  currentPage = signal(0);
  pageSize = signal(20);
  totalElements = signal(0);

  // Filters
  selectedType = signal<UnitType | null>(null);
  searchTerm = signal<string>('');
  selectedUserId = signal<string | null>(null);
  onlyOccupied = signal<boolean>(false);

  // Options
  typeOptions = [
    { value: null, label: 'directory.filters.type.all' },
    { value: 'FLAT' as UnitType, label: 'units.types.FLAT' },
    { value: 'GARAGE' as UnitType, label: 'units.types.GARAGE' },
    { value: 'PARKING' as UnitType, label: 'units.types.PARKING' },
  ] as const;

  // Users list for filter
  availableUsers = signal<UIUser[]>([]);
  selectedUser = signal<UIUser | null>(null);

  stringifyType = (item: any): string => {
    if (!item) return '';
    // Handle both object {value, label} and direct value
    if (item.label) {
      return this.translate.instant(item.label);
    }
    // If it's a direct value, find the corresponding option
    const option = this.typeOptions.find(opt => opt.value === item);
    if (option) {
      return this.translate.instant(option.label);
    }
    return '';
  };

  getSelectedTypeOption(): any {
    const currentType = this.selectedType();
    if (currentType === null) {
      return this.typeOptions[0]; // Return "all" option
    }
    return this.typeOptions.find(opt => opt.value === currentType) || this.typeOptions[0];
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

  constructor(
    @Inject(UNITS_SERVICE_TOKEN) private unitsService: UnitsService,
    @Inject(USERS_SERVICE_TOKEN) private usersService: UsersService,
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
    private translate: TranslateService
  ) { }

  ngOnInit(): void {
    this.loadAvailableUsers();
    this.loadUnits();
  }

  private loadAvailableUsers(): void {
    this.usersService.getUsers().subscribe({
      next: (users: UIUser[]) => {
        this.availableUsers.set(users);
      },
      error: (error) => {
        console.error('Error loading users:', error);
      }
    });
  }

  loadUnits(): void {
    this.loading.set(true);
    this.unitsService
      .getUnitsDirectory(
        this.currentPage(),
        this.pageSize(),
        this.selectedType() || undefined,
        this.searchTerm() || undefined,
        this.selectedUserId() || undefined
      )
      .subscribe({
        next: (paginated: PaginatedUnits) => {
          let units = paginated.content || [];

          // Apply "only occupied" filter if enabled (client-side filtering)
          // Note: This filters only the current page. For accurate pagination across all pages,
          // the filter should be implemented at the API level.
          if (this.onlyOccupied()) {
            units = units.filter((unit: Unit) =>
              unit.members && unit.members.length > 0
            );
          }

          this.units.set(units);
          // Keep original pagination info (based on unfiltered data)
          // This means pagination may show empty pages when filter is active
          this.totalPages.set(paginated.totalPages || 0);
          this.currentPage.set(paginated.number || 0);
          this.totalElements.set(paginated.totalElements || 0);

          // Initialize collapsed signals for all members
          units.forEach((unit: Unit) => {
            (unit.members || []).forEach((member: any) => {
              if (member.user?.id && !this.collapsedStates.has(member.user.id)) {
                this.collapsedStates.set(member.user.id, signal(true));
              }
            });
          });

          this.loading.set(false);
        },
        error: (error) => {
          console.error('Failed to load units:', error);
          this.loading.set(false);
        },
      });
  }

  onTypeChange(selected: any): void {
    // Handle both object {value, label} and direct value
    let type: UnitType | null = null;
    if (selected) {
      if (selected.value !== undefined) {
        type = selected.value;
      } else if (typeof selected === 'string') {
        type = selected as UnitType;
      } else {
        type = selected;
      }
    }
    this.selectedType.set(type);
    this.currentPage.set(0);
    this.loadUnits();
  }

  onSearchChange(): void {
    this.currentPage.set(0);
    this.loadUnits();
  }

  onUserChange(user: UIUser | null): void {
    this.selectedUser.set(user);
    this.selectedUserId.set(user?.id || null);
    this.currentPage.set(0);
    this.loadUnits();
  }

  onOnlyOccupiedChange(checked: boolean): void {
    this.onlyOccupied.set(checked);
    this.currentPage.set(0);
    this.loadUnits();
  }

  onPageChange(page: number): void {
    this.currentPage.set(page);
    this.loadUnits();
  }

  getTypeLabel(type: any): string {
    if (!type) return '';
    const typeValue = typeof type === 'string' ? type : (type?.value || type?.toString() || '');
    if (!typeValue) return '';
    const key = `units.types.${typeValue}`;
    return this.translate.instant(key);
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
      // If it's an object without proper toString, try to get the enum value
      roleValue = Object.values(role)[0] as string || '';
    }
    if (!roleValue || roleValue === '[object Object]') return '';
    const key = `units.residenceRoles.${roleValue}`;
    return this.translate.instant(key);
  }

  isPrimaryUnit(unit: Unit, member: any): boolean {
    if (!unit || !member?.user) {
      return false;
    }
    return member.user.primaryUnitId === unit.id;
  }

  // Track collapsed state for each member using signals
  collapsedStates = new Map<string, ReturnType<typeof signal<boolean>>>();

  getCollapsedSignal(memberId: string): ReturnType<typeof signal<boolean>> {
    if (!this.collapsedStates.has(memberId)) {
      this.collapsedStates.set(memberId, signal(true));
    }
    return this.collapsedStates.get(memberId)!;
  }

  toggleCollapsed(memberId: string): void {
    const collapsedSignal = this.getCollapsedSignal(memberId);
    collapsedSignal.set(!collapsedSignal());
  }

  canShowContactInfo(member: any): boolean {
    return member?.user?.profileSharingConsent === true;
  }
}

