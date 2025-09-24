import { CommonModule, NgIf } from '@angular/common';
import { Component, HostListener, Inject, OnDestroy } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { TuiNotification } from '@taiga-ui/core';
import { UIApplication } from '../../../../models/UIApplication';
import { APPLICATIONS_SERVICE_TOKEN } from '../../hub.provider';
import { ApplicationsService } from '../../services/applications.service';
import { ApplicationComponent } from '../application/application.component';

@Component({
  selector: 'applications',
  imports: [ApplicationComponent, TuiNotification, TranslateModule, CommonModule, NgIf],
  templateUrl: './applications.component.html',
  styleUrl: './applications.component.scss'
})
export class ApplicationsComponent implements OnDestroy {
  applications: UIApplication[] = [];
  showNotification = true;
  private hideThreshold = 200; // pixels from top to hide notification
  private showThreshold = 100; // pixels from top to show notification
  private isMobile = false;

  constructor(
    @Inject(APPLICATIONS_SERVICE_TOKEN) private applicationsService: ApplicationsService,
  ) {
    this.applicationsService.getApplications().subscribe(applications => {
      this.applications = applications;
    });

    // Check if mobile on initialization
    this.checkMobile();
  }

  @HostListener('window:scroll', ['$event'])
  onWindowScroll() {
    // Always hide notification on mobile
    if (this.isMobile) {
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
