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
        // Filter applications based on search text
        let filteredApplications = applications;
        if (searchText && searchText.trim()) {
          const searchLower = searchText.toLowerCase().trim();
          filteredApplications = applications.filter(application =>
            application.id?.toString().toLowerCase().includes(searchLower) ||
            application.name?.toLowerCase().includes(searchLower) ||
            application.url?.toLowerCase().includes(searchLower) ||
            application.helpText?.toLowerCase().includes(searchLower)
          );
        }

        // Sort applications if sortBy is specified
        if (sortBy) {
          filteredApplications = [...filteredApplications].sort((a, b) => {
            const aValue = a[sortBy as keyof UIApplication];
            const bValue = b[sortBy as keyof UIApplication];

            // Handle undefined values
            if (aValue === undefined && bValue === undefined) return 0;
            if (aValue === undefined) return 1;
            if (bValue === undefined) return -1;

            if (aValue === bValue) return 0;

            const comparison = aValue < bValue ? -1 : 1;
            return sortOrder === 'desc' ? -comparison : comparison;
          });
        }

        // Calculate pagination
        const totalItems = filteredApplications.length;
        const itemsPerPage = pageSize || 12;
        const totalPages = Math.ceil(totalItems / itemsPerPage);
        const currentPage = page || 1;
        const startIndex = (currentPage - 1) * itemsPerPage;
        const endIndex = startIndex + itemsPerPage;
        const paginatedApplications = filteredApplications.slice(startIndex, endIndex);

        return {
          totalPages,
          totalItems,
          currentPage,
          itemsPerPage,
          items: paginatedApplications
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

