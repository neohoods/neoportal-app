import { Component, Inject, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import {
  TuiAlertService,
  TuiButton,
  TuiIcon,
  TuiTextfield,
} from '@taiga-ui/core';
import { TuiPassword, TuiSwitch } from '@taiga-ui/kit';
import { UIApplication } from '../../../../models/UIApplication';
import { APPLICATIONS_SERVICE_TOKEN } from '../../admin.providers';
import { ApplicationsService } from '../../services/applications.service';
@Component({
  standalone: true,
  selector: 'app-applications-edit',
  imports: [
    RouterLink,
    TuiButton,
    FormsModule,
    ReactiveFormsModule,
    TuiButton,
    TuiTextfield,
    TuiIcon,
    TuiPassword,
    TuiSwitch,
    TranslateModule
  ],
  templateUrl: './applications-edit.component.html',
  styleUrl: './applications-edit.component.scss',
})
export class ApplicationsEditComponent implements OnInit {
  application: UIApplication = {} as UIApplication;
  editApplicationForm: FormGroup;
  applicationId: string | null = null;

  constructor(
    private route: ActivatedRoute,
    @Inject(APPLICATIONS_SERVICE_TOKEN) private applicationsService: ApplicationsService,
    private fb: FormBuilder,
    private alerts: TuiAlertService,
    private router: Router,
  ) {
    this.editApplicationForm = this.fb.group({
      name: ['', Validators.required],
      url: ['', [Validators.required]],
      icon: ['', [Validators.required]],
      helpText: ['', [Validators.required]],
      disabled: [false],
    });
  }

  ngOnInit() {
    this.applicationId = this.route.snapshot.paramMap.get('id');
    if (this.applicationId) {
      this.applicationsService.getApplication(this.applicationId).subscribe((application) => {
        this.application = application;
        this.editApplicationForm.patchValue(this.application);
      });
    } else {
      this.editApplicationForm = this.fb.group({
        name: ['', Validators.required],
        url: ['', [Validators.required]],
        icon: ['', [Validators.required]],
        helpText: ['', [Validators.required]],
        disabled: [false],
      });
    }
  }

  onSubmit() {
    if (this.editApplicationForm.valid) {
      const updatedApplication = this.editApplicationForm.value;
      updatedApplication.id = this.application.id;
      this.application.name = updatedApplication.name;
      this.application.url = updatedApplication.url;
      this.application.icon = updatedApplication.icon;
      this.application.helpText = updatedApplication.helpText;
      this.application.disabled = updatedApplication.disabled;
      if (this.applicationId) {
        this.applicationsService.updateApplication(this.application.id, this.application).subscribe();
      } else {
        this.applicationsService.createApplication(this.application).subscribe();
      }

      this.alerts
        .open(`Successfully saved ${this.application.name}`, {
          appearance: 'positive',
        })
        .subscribe();
      this.router.navigate(['/admin/applications']);
    }
  }
}
