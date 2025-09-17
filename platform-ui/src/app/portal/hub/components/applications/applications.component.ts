import { Component, Inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { TuiNotification } from '@taiga-ui/core';
import { UIApplication } from '../../../../models/UIApplication';
import { APPLICATIONS_SERVICE_TOKEN } from '../../hub.provider';
import { ApplicationsService } from '../../services/applications.service';
import { ApplicationComponent } from '../application/application.component';

@Component({
  selector: 'applications',
  imports: [ApplicationComponent, TuiNotification, TranslateModule],
  templateUrl: './applications.component.html',
  styleUrl: './applications.component.scss'
})
export class ApplicationsComponent {
  applications: UIApplication[] = [];

  constructor(
    @Inject(APPLICATIONS_SERVICE_TOKEN) private applicationsService: ApplicationsService,
  ) {
    this.applicationsService.getApplications().subscribe(applications => {
      this.applications = applications;
    });
  }
}
