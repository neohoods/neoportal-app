import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, Inject, OnInit, signal } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiButton, TuiExpand, TuiIcon } from '@taiga-ui/core';
import { TuiChevron } from '@taiga-ui/kit';
import { UIContactNumber, UIInfo } from '../../../../models/UIInfos';
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
    contactNumbers: []
  });

  // Signaux pour chaque délégué (Map avec l'index comme clé)
  public readonly collapsedDelegates = new Map<number, ReturnType<typeof signal>>();

  // Signaux pour chaque contact syndic (Map avec l'index comme clé)
  public readonly collapsedSyndic = new Map<number, ReturnType<typeof signal>>();

  // Signaux pour chaque contact d'urgence (Map avec l'index comme clé)
  public readonly collapsedEmergency = new Map<number, ReturnType<typeof signal>>();

  // Signaux pour chaque contact de maintenance (Map avec l'index comme clé)
  public readonly collapsedMaintenance = new Map<number, ReturnType<typeof signal>>();

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
      // Normaliser les données pour gérer la compatibilité avec l'ancienne structure
      const normalizedInfos = this.normalizeInfosData(infos);
      this.infos.set(normalizedInfos);
      this.initializeSignals();
      this.cdr.detectChanges(); // Force change detection
    });
  }

  /**
   * Normalise les données pour s'assurer que contactNumbers est toujours un array
   */
  private normalizeInfosData(infos: any): UIInfo {
    const normalized = { ...infos };

    // S'assurer que contactNumbers est un array
    if (!Array.isArray(normalized.contactNumbers)) {
      if (normalized.contactNumbers && typeof normalized.contactNumbers === 'object') {
        // Si c'est l'ancienne structure avec syndic/emergency séparés
        const contacts: UIContactNumber[] = [];

        // Ajouter les contacts syndic
        if (normalized.contactNumbers.syndic) {
          const syndicContacts = Array.isArray(normalized.contactNumbers.syndic)
            ? normalized.contactNumbers.syndic
            : [normalized.contactNumbers.syndic];
          syndicContacts.forEach((contact: any) => {
            contacts.push({ ...contact, contactType: 'syndic' });
          });
        }

        // Ajouter les contacts d'urgence
        if (normalized.contactNumbers.emergency) {
          const emergencyContacts = Array.isArray(normalized.contactNumbers.emergency)
            ? normalized.contactNumbers.emergency
            : [normalized.contactNumbers.emergency];
          emergencyContacts.forEach((contact: any) => {
            contacts.push({ ...contact, contactType: 'emergency' });
          });
        }

        // Ajouter les contacts de maintenance
        if (normalized.contactNumbers.maintenance) {
          const maintenanceContacts = Array.isArray(normalized.contactNumbers.maintenance)
            ? normalized.contactNumbers.maintenance
            : [normalized.contactNumbers.maintenance];
          maintenanceContacts.forEach((contact: any) => {
            contacts.push({ ...contact, contactType: 'maintenance' });
          });
        }

        normalized.contactNumbers = contacts;
      } else {
        // Si contactNumbers est null/undefined, initialiser comme array vide
        normalized.contactNumbers = [];
      }
    }

    return normalized;
  }


  private initializeSignals() {
    // Initialiser les signaux pour les délégués
    this.infos().delegates.forEach((_, index) => {
      this.collapsedDelegates.set(index, signal(true));
    });

    // Initialiser les signaux pour les contacts syndic
    const syndicContacts = this.getSyndicContacts();
    syndicContacts.forEach((_, index) => {
      this.collapsedSyndic.set(index, signal(true));
    });

    // Initialiser les signaux pour les contacts d'urgence
    const emergencyContacts = this.getEmergencyContacts();
    emergencyContacts.forEach((_, index) => {
      this.collapsedEmergency.set(index, signal(true));
    });

    // Initialiser les signaux pour les contacts de maintenance
    const maintenanceContacts = this.getMaintenanceContacts();
    maintenanceContacts.forEach((_, index) => {
      this.collapsedMaintenance.set(index, signal(true));
    });
  }

  // Méthodes helper pour obtenir les signaux
  getDelegateSignal(index: number) {
    return this.collapsedDelegates.get(index) || signal(true);
  }

  getSyndicSignal(index: number) {
    return this.collapsedSyndic.get(index) || signal(true);
  }

  getEmergencySignal(index: number) {
    return this.collapsedEmergency.get(index) || signal(true);
  }

  // Méthodes pour toggle les états
  toggleDelegate(index: number) {
    const signal = this.getDelegateSignal(index);
    signal.set(!signal());
  }

  toggleSyndic(index: number) {
    const signal = this.getSyndicSignal(index);
    signal.set(!signal());
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

  // Méthodes pour filtrer les contacts par type
  getSyndicContacts(): UIContactNumber[] {
    const contactNumbers = this.infos().contactNumbers;
    return Array.isArray(contactNumbers) ? contactNumbers.filter(contact => contact.contactType === 'syndic') : [];
  }

  getEmergencyContacts(): UIContactNumber[] {
    const contactNumbers = this.infos().contactNumbers;
    return Array.isArray(contactNumbers) ? contactNumbers.filter(contact => contact.contactType === 'emergency') : [];
  }

  getMaintenanceContacts(): UIContactNumber[] {
    const contactNumbers = this.infos().contactNumbers;
    return Array.isArray(contactNumbers) ? contactNumbers.filter(contact => contact.contactType === 'maintenance') : [];
  }

  // Méthode pour vérifier si des contacts syndic existent
  hasSyndicContacts(): boolean {
    return this.getSyndicContacts().length > 0;
  }

  // Méthodes pour les signaux de maintenance
  getMaintenanceSignal(index: number) {
    return this.collapsedMaintenance.get(index) || signal(true);
  }

  toggleMaintenance(index: number) {
    const currentSignal = this.collapsedMaintenance.get(index);
    if (currentSignal) {
      currentSignal.set(!currentSignal());
    }
  }
}
