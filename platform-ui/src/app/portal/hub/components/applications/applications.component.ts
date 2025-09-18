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

  constructor(
    @Inject(APPLICATIONS_SERVICE_TOKEN) private applicationsService: ApplicationsService,
  ) {
    this.applicationsService.getApplications().subscribe(applications => {
      this.applications = applications;
    });
  }

  @HostListener('window:scroll', ['$event'])
  onWindowScroll() {
    const scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;

    if (scrollTop >= this.hideThreshold) {
      this.showNotification = false;
    } else if (scrollTop <= this.showThreshold) {
      this.showNotification = true;
    }
    // Entre les deux seuils, on garde l'état actuel (hystérésis)
  }

  ngOnDestroy() {
    // Cleanup if needed
  }
}
