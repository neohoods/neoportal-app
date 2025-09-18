import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, Inject, OnInit, signal } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiButton, TuiExpand, TuiIcon } from '@taiga-ui/core';
import { TuiChevron } from '@taiga-ui/kit';
import { UIInfo } from '../../../../models/UIInfos';
import { INFOS_SERVICE_TOKEN } from '../../hub.provider';
import { InfosService } from '../../services/infos.service';

@Component({
  selector: 'infos',
  imports: [
    CommonModule,
    TuiExpand,
    TuiButton,
    TuiChevron,
    TranslateModule,
    TuiIcon],
  templateUrl: './infos.component.html',
  styleUrl: './infos.component.scss'
})
export class InfosComponent implements OnInit {
  infos = signal<UIInfo>({
    id: '1',
    nextAGDate: null,
    rulesUrl: '',
    delegates: [],
    contactNumbers: {
      syndic: {
        type: '',
        description: '',
        availability: '',
        responseTime: '',
        name: '',
        phoneNumber: '',
        email: '',
        officeHours: '',
        address: '',
      },
      emergency: [],
    }
  });

  // Signaux pour chaque délégué (Map avec l'index comme clé)
  public readonly collapsedDelegates = new Map<number, ReturnType<typeof signal>>();

  // Signal pour le syndic
  public readonly collapsedSyndic = signal(true);

  // Signaux pour chaque contact d'urgence (Map avec l'index comme clé)
  public readonly collapsedEmergency = new Map<number, ReturnType<typeof signal>>();

  constructor(
    @Inject(INFOS_SERVICE_TOKEN) private infosService: InfosService,
    private cdr: ChangeDetectorRef,
    private translateService: TranslateService,
  ) { }

  ngOnInit(): void {
    this.loadInfos();
  }

  private loadInfos(): void {
    this.infosService.getInfos().subscribe(infos => {
      this.infos.set(infos);
      this.initializeSignals();
      this.cdr.detectChanges(); // Force change detection
    });
  }

  private initializeSignals() {
    // Initialiser les signaux pour les délégués
    this.infos().delegates.forEach((_, index) => {
      this.collapsedDelegates.set(index, signal(true));
    });

    // Initialiser les signaux pour les contacts d'urgence
    this.infos().contactNumbers.emergency?.forEach((_, index) => {
      this.collapsedEmergency.set(index, signal(true));
    });
  }

  // Méthodes helper pour obtenir les signaux
  getDelegateSignal(index: number) {
    return this.collapsedDelegates.get(index) || signal(true);
  }

  getEmergencySignal(index: number) {
    return this.collapsedEmergency.get(index) || signal(true);
  }

  // Méthodes pour toggle les états
  toggleDelegate(index: number) {
    const signal = this.getDelegateSignal(index);
    signal.set(!signal());
  }

  toggleSyndic() {
    this.collapsedSyndic.set(!this.collapsedSyndic());
  }

  toggleEmergency(index: number) {
    const signal = this.getEmergencySignal(index);
    signal.set(!signal());
  }

  // Méthode pour obtenir la locale actuelle
  getCurrentLocale(): string {
    return this.translateService.currentLang || 'fr';
  }

  // Méthode pour formater la date selon la locale
  formatDate(dateString: string | null): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    const locale = this.getCurrentLocale();

    return date.toLocaleDateString(locale, {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  }

  // Méthode pour formater l'heure selon la locale
  formatTime(dateString: string | null): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    const locale = this.getCurrentLocale();

    return date.toLocaleTimeString(locale, {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    });
  }
}
