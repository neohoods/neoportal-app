import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
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
  TuiNotification,
  TuiRoot
} from '@taiga-ui/core';
import {
  TuiAvatar,
  TuiChevron,
  TuiFade,
  TuiTabs
} from '@taiga-ui/kit';
import { tuiLayoutIconsProvider, TuiNavigation } from '@taiga-ui/layout';
import { FooterComponent } from '../../../components/footer/footer.component';
import { NotificationsPopupComponent } from '../../../components/notifications-popup/notifications-popup.component';
import { ProfileCompletionCheckComponent } from '../../../components/profile-completion-check/profile-completion-check.component';
import { AUTH_SERVICE_TOKEN } from '../../../global.provider';
import { AuthService, UserInfo } from '../../../services/auth.service';
import { ConfigService } from '../../../services/config.service';
import { ApplicationsComponent } from '../components/applications/applications.component';


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
    TuiNotification,
    TuiTabs,
    NotificationsPopupComponent,
    ProfileCompletionCheckComponent,
    TranslateModule,
    TuiAvatar,
    TuiInitialsPipe,
    TuiAutoColorPipe,
    ApplicationsComponent,
  ],
  templateUrl: './hub-layout.component.html',
  styleUrl: './hub-layout.component.scss',
  providers: [
    tuiLayoutIconsProvider({ grid: '@tui.align-justify' }),
    TuiDropdownService,
    tuiAsPortal(TuiDropdownService),
  ],
})
export default class HubLayoutComponent extends TuiPortals implements OnInit, OnDestroy {
  protected expanded = false;
  protected open = false;
  protected switch = false;
  protected readonly routes: any = {};
  totalItemsCurrentlyBorrowed: number = 0;
  totalApprovalRequests: number = 0;
  appConfig = ConfigService.configuration;

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
    // Force layout recalculation when switching from admin to hub
    setTimeout(() => {
      window.dispatchEvent(new Event('resize'));
    }, 0);
  }

  ngOnDestroy() {
  }
}
