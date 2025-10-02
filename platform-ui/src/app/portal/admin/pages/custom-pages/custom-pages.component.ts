import { Component, Inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterModule } from '@angular/router';
import { TuiResponsiveDialogService } from '@taiga-ui/addon-mobile';
import { TuiTable } from '@taiga-ui/addon-table';
import { TuiAlertService, TuiButton } from '@taiga-ui/core';
import { TUI_CONFIRM, TuiConfirmData } from '@taiga-ui/kit';
import { map } from 'rxjs';
import { UICustomPage } from '../../../../models/UICustomPage';
import { CUSTOM_PAGES_SERVICE_TOKEN } from '../../admin.providers';
import { Column, TosTableComponent } from '../../components/tos-table/tos-table.component';
import { CustomPagesService } from '../../services/custom-pages.service';

@Component({
  selector: 'app-custom-pages',
  imports: [
    RouterLink,
    RouterModule,
    FormsModule,
    TuiTable,
    TuiButton,
    TosTableComponent,
  ],
  templateUrl: './custom-pages.component.html',
  styleUrl: './custom-pages.component.scss'
})
export class CustomPagesComponent implements OnInit {

  columns: Column[] = [
    {
      key: 'ref',
      label: 'Reference',
      visible: true,
      sortable: true,
      size: 'm',
    },
    { key: 'title', label: 'Title', visible: true, sortable: true, size: 'm' },
    {
      key: 'position',
      label: 'Position',
      visible: true,
      sortable: true,
      size: 'm',
    },
    {
      key: 'content',
      label: 'Content',
      visible: false,
      sortable: false,
      size: 'l',
    }
  ];

  constructor(@Inject(CUSTOM_PAGES_SERVICE_TOKEN) private customPagesService: CustomPagesService,
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
    return this.customPagesService.getCustomPageRefs().pipe(
      map((pages: UICustomPage[]) => {
        // Add help-center page
        const allPages = [...pages, {
          id: 'help-center',
          ref: 'help-center',
          position: 'footer-links',
          title: 'Help Center',
          content: 'Test',
          order: 0
        }];

        // Filter pages based on search text
        let filteredPages = allPages;
        if (searchText && searchText.trim()) {
          const searchLower = searchText.toLowerCase().trim();
          filteredPages = allPages.filter(page =>
            page.ref?.toLowerCase().includes(searchLower) ||
            page.title?.toLowerCase().includes(searchLower) ||
            page.position?.toLowerCase().includes(searchLower) ||
            page.content?.toLowerCase().includes(searchLower)
          );
        }

        // Sort pages if sortBy is specified
        if (sortBy) {
          filteredPages = [...filteredPages].sort((a, b) => {
            const aValue = a[sortBy as keyof UICustomPage];
            const bValue = b[sortBy as keyof UICustomPage];

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
        const totalItems = filteredPages.length;
        const itemsPerPage = pageSize || 12;
        const totalPages = Math.ceil(totalItems / itemsPerPage);
        const currentPage = page || 1;
        const startIndex = (currentPage - 1) * itemsPerPage;
        const endIndex = startIndex + itemsPerPage;
        const paginatedPages = filteredPages.slice(startIndex, endIndex);

        return {
          totalPages,
          totalItems,
          currentPage,
          itemsPerPage,
          items: paginatedPages
        }
      })
    );
  }

  deletePage(page: UICustomPage) {
    const data: TuiConfirmData = {
      content: 'Are you sure you want to delete this page?', // Simple content
      yes: 'Yes, Delete',
      no: 'Cancel',
    };

    this.dialogs
      .open<boolean>(TUI_CONFIRM, {
        label: "Delete page '" + page.title + "'",
        size: 'm',
        data,
      })
      .subscribe((response) => {
        if (response) {
          this.customPagesService.deleteCustomPage(page.id).subscribe();
          this.alerts.open(
            'Page <strong>' +
            page.title +
            '</strong> deleted successfully',
            { appearance: 'positive' },
          ).subscribe();
        }
      });
  }
}

