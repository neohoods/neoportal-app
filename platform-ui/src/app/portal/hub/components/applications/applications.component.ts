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
  private scrollThreshold = 50; // pixels from top

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
    this.showNotification = scrollTop < this.scrollThreshold;
  }

  ngOnDestroy() {
    // Cleanup if needed
  }
}
