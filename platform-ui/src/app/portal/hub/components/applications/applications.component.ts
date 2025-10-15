import { CommonModule, NgIf } from '@angular/common';
import { Component, HostListener, Inject, OnDestroy, OnInit } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { TuiButton, TuiNotification } from '@taiga-ui/core';
import { UIApplication } from '../../../../models/UIApplication';
import { APPLICATIONS_SERVICE_TOKEN } from '../../hub.provider';
import { ApplicationsService } from '../../services/applications.service';
import { CookieService } from '../../services/cookie.service';
import { ApplicationComponent } from '../application/application.component';

@Component({
  selector: 'applications',
  imports: [ApplicationComponent, TuiNotification, TuiButton, TranslateModule, CommonModule, NgIf],
  templateUrl: './applications.component.html',
  styleUrl: './applications.component.scss'
})
export class ApplicationsComponent implements OnInit, OnDestroy {
  applications: UIApplication[] = [];
  showNotification = true;
  private hideThreshold = 200; // pixels from top to hide notification
  private showThreshold = 100; // pixels from top to show notification
  private isMobile = false;

  constructor(
    @Inject(APPLICATIONS_SERVICE_TOKEN) private applicationsService: ApplicationsService,
    private cookieService: CookieService,
  ) {
    this.applicationsService.getApplications().subscribe(applications => {
      this.applications = applications;
    });

    // Check if mobile on initialization
    this.checkMobile();
  }

  ngOnInit(): void {
    this.checkNotificationState();
  }

  /**
   * Vérifie l'état de la notification depuis les cookies
   */
  private checkNotificationState(): void {
    const isNotificationClosed = this.cookieService.isApplicationsNotificationClosed();
    if (isNotificationClosed) {
      this.showNotification = false;
    }
  }

  /**
   * Ferme la notification et mémorise le choix dans un cookie
   */
  closeNotification(): void {
    this.showNotification = false;
    this.cookieService.setApplicationsNotificationClosed();
  }

  @HostListener('window:scroll', ['$event'])
  onWindowScroll() {
    // Always hide notification on mobile
    if (this.isMobile) {
      this.showNotification = false;
      return;
    }

    // Si l'utilisateur a fermé la notification, ne pas la réafficher
    const isNotificationClosed = this.cookieService.isApplicationsNotificationClosed();
    if (isNotificationClosed) {
      this.showNotification = false;
      return;
    }

    const scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;

    if (scrollTop >= this.hideThreshold) {
      this.showNotification = false;
    } else if (scrollTop <= this.showThreshold) {
      this.showNotification = true;
    }
    // Entre les deux seuils, on garde l'état actuel (hystérésis)
  }

  @HostListener('window:resize', ['$event'])
  onWindowResize() {
    this.checkMobile();
  }

  private checkMobile() {
    this.isMobile = window.innerWidth <= 768;
    // Always hide notification on mobile
    if (this.isMobile) {
      this.showNotification = false;
    }
  }

  ngOnDestroy() {
    // Cleanup if needed
  }
}
