import { Component, Inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterModule } from '@angular/router';
import { TuiResponsiveDialogService } from '@taiga-ui/addon-mobile';
import { TuiTable } from '@taiga-ui/addon-table';
import { TuiAlertService, TuiButton } from '@taiga-ui/core';
import { TUI_CONFIRM, TuiConfirmData } from '@taiga-ui/kit';
import { map } from 'rxjs';
import { UIApplication } from '../../../../models/UIApplication';
import { APPLICATIONS_SERVICE_TOKEN } from '../../admin.providers';
import { Column, TosTableComponent } from '../../components/tos-table/tos-table.component';
import { ApplicationsService } from '../../services/applications.service';

@Component({
  selector: 'app-applications',
  imports: [
    RouterLink,
    RouterModule,
    FormsModule,
    TuiTable,
    TuiButton,
    TosTableComponent,
  ],
  templateUrl: './applications.component.html',
  styleUrl: './applications.component.scss'
})
export class ApplicationsComponent implements OnInit {

  columns: Column[] = [
    {
      key: 'id',
      label: 'ID',
      visible: true,
      sortable: true,
      size: 'm',
    },
    { key: 'name', label: 'Name', visible: true, sortable: true, size: 'm' },
    {
      key: 'url',
      label: 'URL',
      visible: true,
      sortable: true,
      size: 'm',
    },
    {
      key: 'icon',
      label: 'Icon',
      visible: true,
      sortable: false,
      custom: true,
      size: 's',
    },
    {
      key: 'helpText',
      label: 'Help Text',
      visible: false,
      sortable: false,
      size: 'l',
    }
  ];

  constructor(@Inject(APPLICATIONS_SERVICE_TOKEN) private applicationsService: ApplicationsService,
    private dialogs: TuiResponsiveDialogService,
    private alerts: TuiAlertService,) {
  }

  ngOnInit() {
  }

  getDataFunction = (
    searchText?: string,
    sortBy?: string,
    sortOrder?: 'asc' | 'desc',
    page?: number,
    pageSize?: number
  ) => {
    return this.applicationsService.getApplications().pipe(
      map((applications: UIApplication[]) => {
        return {
          totalPages: 1,
          totalItems: applications.length,
          currentPage: 0,
          itemsPerPage: applications.length,
          items: applications
        }
      })
    );
  }

  deleteApplication(application: UIApplication) {
    const data: TuiConfirmData = {
      content: 'Are you sure you want to delete this page?', // Simple content
      yes: 'Yes, Delete',
      no: 'Cancel',
    };

    this.dialogs
      .open<boolean>(TUI_CONFIRM, {
        label: "Delete application '" + application.name + "'",
        size: 'm',
        data,
      })
      .subscribe((response) => {
        if (response) {
          this.applicationsService.deleteApplication(application.id).subscribe();
          this.alerts.open(
            'Application <strong>' +
            application.name +
            '</strong> deleted successfully',
            { appearance: 'positive' },
          ).subscribe();
        }
      });
  }
}

