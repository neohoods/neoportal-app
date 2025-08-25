import { Component, computed, effect, Inject, OnDestroy, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { tuiAsPortal, TuiPortals } from '@taiga-ui/cdk';
import {
  TuiAppearance,
  TuiAutoColorPipe,
  TuiButton,
  TuiDataList,
  TuiDropdown,
  TuiDropdownService,
  TuiIcon,
  TuiInitialsPipe,
  TuiRoot
} from '@taiga-ui/core';
import {
  TuiAvatar,
  TuiBadgeNotification,
  TuiChevron,
  TuiFade,
  TuiTabs
} from '@taiga-ui/kit';
import { tuiLayoutIconsProvider, TuiNavigation } from '@taiga-ui/layout';
import { FooterComponent } from '../../../components/footer/footer.component';
import { AUTH_SERVICE_TOKEN } from '../../../global.provider';
import { AuthService, UserInfo } from '../../../services/auth.service';


@Component({
  standalone: true,
  selector: 'hub-layout',
  imports: [
    FooterComponent,
    RouterLinkActive,
    RouterOutlet,
    TuiRoot,
    FormsModule,
    RouterLink,
    TuiAppearance,
    TuiButton,
    TuiChevron,
    TuiAppearance,
    TuiDataList,
    TuiDropdown,
    TuiFade,
    TuiIcon,
    TuiNavigation,
    TuiTabs,
    TuiBadgeNotification,
    TranslateModule,
    TuiAvatar,
    TuiInitialsPipe,
    TuiAutoColorPipe,
  ],
  templateUrl: './hub-layout.component.html',
  styleUrl: './hub-layout.component.scss',
  providers: [
    tuiLayoutIconsProvider({ grid: '@tui.align-justify' }),
    TuiDropdownService,
    tuiAsPortal(TuiDropdownService),
  ],
})
export default class HubLayoutComponent extends TuiPortals implements OnDestroy {
  protected expanded = false;
  protected open = false;
  protected switch = false;
  protected readonly routes: any = {};
  totalItemsCurrentlyBorrowed: number = 0;
  totalApprovalRequests: number = 0;

  user: UserInfo;


  constructor(
    @Inject(AUTH_SERVICE_TOKEN) private authService: AuthService,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    super();
    this.user = this.authService.getCurrentUserInfo();
  }

  ngOnInit() {
  }

  ngOnDestroy() {
  }
}
