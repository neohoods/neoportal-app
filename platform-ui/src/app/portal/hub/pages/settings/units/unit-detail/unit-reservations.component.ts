import { CommonModule, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiLoader, TuiNotification } from '@taiga-ui/core';
import { TuiChip, TuiPagination } from '@taiga-ui/kit';
import { UnitsHubApiService } from '../../../../../../api-client/api/unitsHubApi.service';
import { Reservation } from '../../../../../../api-client/model/reservation';
import { fromApiReservation } from '../../../../../../models/UIReservation';
import { UIReservation } from '../../../../../../models/UIReservation';

@Component({
  selector: 'app-unit-reservations',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    RouterLink,
    TranslateModule,
    TuiButton,
    TuiIcon,
    TuiLoader,
    TuiNotification,
    TuiChip,
    TuiPagination
  ],
  templateUrl: './unit-reservations.component.html',
  styleUrl: './unit-reservations.component.scss'
})
export class UnitReservationsComponent implements OnInit {
  unitId: string | null = null;
  reservations = signal<UIReservation[]>([]);
  loading = signal(true);
  error: string | null = null;
  currentPage = signal(0);
  pageSize = 20;
  totalElements = signal(0);
  Math = Math;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private unitsApiService = inject(UnitsHubApiService);
  private translate = inject(TranslateService);
  private alerts = inject(TuiAlertService);

  constructor() {}

  ngOnInit(): void {
    this.unitId = this.route.snapshot.paramMap.get('id');
    if (this.unitId) {
      this.loadReservations();
    } else {
      this.router.navigate(['/hub/settings/units']);
    }
  }

  loadReservations(): void {
    if (!this.unitId) return;

    this.loading.set(true);
    this.error = null;

    // Note: getUnitReservations will be available after OpenAPI regeneration
    // For now, using a workaround - the method will be added to UnitsHubApiService
    (this.unitsApiService as any).getUnitReservations(this.unitId, this.currentPage(), this.pageSize).subscribe({
      next: (response: any) => {
        const reservations = (response.content || []).map((r: Reservation) => fromApiReservation(r));
        this.reservations.set(reservations);
        this.totalElements.set(response.totalElements || 0);
        this.loading.set(false);
      },
      error: (error: any) => {
        console.error('Failed to load unit reservations:', error);
        this.error = this.translate.instant('units.reservations.loadError');
        this.loading.set(false);
      }
    });
  }

  onPageChange(page: number): void {
    this.currentPage.set(page);
    this.loadReservations();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  getStatusLabel(status: string): string {
    return this.translate.instant(`reservations.status.${status.toLowerCase()}`);
  }

  getStatusAppearance(status: string): string {
    const statusMap: { [key: string]: string } = {
      'PENDING_PAYMENT': 'warning',
      'CONFIRMED': 'info',
      'ACTIVE': 'success',
      'COMPLETED': 'neutral',
      'CANCELLED': 'error',
      'REFUNDED': 'neutral'
    };
    return statusMap[status] || 'neutral';
  }

  formatDate(date: string): string {
    if (!date) return '';
    const d = new Date(date);
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  viewReservation(reservationId: string): void {
    this.router.navigate(['/spaces/reservations', reservationId]);
  }

  onBack(): void {
    if (this.unitId) {
      this.router.navigate(['/hub/settings/units', this.unitId]);
    } else {
      this.router.navigate(['/hub/settings/units']);
    }
  }
}
