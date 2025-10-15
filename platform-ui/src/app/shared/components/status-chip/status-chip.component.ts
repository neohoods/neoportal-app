import { CommonModule } from '@angular/common';
import { Component, inject, Input } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiSizeXXS } from '@taiga-ui/core';
import { TuiChip } from '@taiga-ui/kit';

@Component({
  selector: 'app-status-chip',
  standalone: true,
  imports: [CommonModule, TuiChip, TranslateModule],
  template: `
    <tui-chip
      [appearance]="getStatusAppearance(status)"
      [size]="size"
    >
      {{ getTranslatedStatus(status) }}
    </tui-chip>
  `
})
export class StatusChipComponent {
  @Input() status!: string;
  @Input() size: TuiSizeXXS = 'm';

  private translate = inject(TranslateService);

  getStatusAppearance(status: string): string {
    switch (status) {
      case 'PENDING_PAYMENT':
        return 'warning'; // Orange - en attente de paiement
      case 'PAYMENT_FAILED':
        return 'negative'; // Rouge - échec de paiement
      case 'EXPIRED':
        return 'negative'; // Rouge - expiré
      case 'CONFIRMED':
        return 'positive'; // Vert - confirmé
      case 'ACTIVE':
        return 'accent'; // Bleu - actif
      case 'COMPLETED':
        return 'positive'; // Vert - terminé
      case 'CANCELLED':
        return 'negative'; // Rouge - annulé
      case 'REFUNDED':
        return 'info'; // Bleu clair - remboursé
      default:
        return 'outline'; // Gris - statut inconnu
    }
  }

  getTranslatedStatus(status: string): string {
    return this.translate.instant(`reservations.status.${status}`);
  }
}
