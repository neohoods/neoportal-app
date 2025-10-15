import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, Inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiDialogService, TuiExpand, TuiIcon, TuiLabel, TuiNotification, TuiTextfield } from '@taiga-ui/core';
import { TUI_CONFIRM, TuiConfirmData, TuiPagination, TuiTabs } from '@taiga-ui/kit';
import {
  TuiInputColorModule
} from '@taiga-ui/legacy';
import { QuillModule } from 'ngx-quill';
import { getGlobalProviders } from '../../../../global.provider';
import { CATEGORY_INFO, CategoryInfo, UIAnnouncement, UIAnnouncementCategory, UIPaginatedAnnouncementsResponse, UIPaginationParams } from '../../../../models/UIAnnoncements';
import { ANNOUNCEMENTS_SERVICE_TOKEN } from '../../hub.provider';
import { AnnouncementsService } from '../../services/annoncements.service';
import { CookieService } from '../../services/cookie.service';
import { AnnouncementComponent } from '../announcement/announcement.component';

@Component({
  selector: 'announcements',
  templateUrl: './announcements.component.html',
  styleUrls: ['./announcements.component.scss'],
  imports: [
    QuillModule,
    TuiInputColorModule,
    TuiTextfield,
    TuiLabel,
    FormsModule,
    ReactiveFormsModule,
    TuiButton,
    TranslateModule,
    AnnouncementComponent,
    CommonModule,
    TuiExpand,
    TuiButton, TuiNotification,
    TuiNotification,
    TuiIcon,
    TuiPagination,
    TuiTabs
  ], providers: [...getGlobalProviders()]
})
export class AnnouncementsComponent implements OnInit {
  pageForm: FormGroup;
  public readonly collapsed = signal(true);
  public readonly showNotification = signal(true);

  announcements = signal<UIAnnouncement[]>([]);
  paginationData = signal<UIPaginatedAnnouncementsResponse | null>(null);
  paginationParams = signal<UIPaginationParams>({
    page: 1,
    pageSize: 10
  });
  protected activeItemIndex = 0;
  protected activePostCategory: UIAnnouncementCategory = UIAnnouncementCategory.CommunityEvent;
  editorConfig = {
    toolbar: [
      ['bold', 'italic', 'underline', 'strike'], // Text formatting
      [{ header: 1 }, { header: 2 }], // Headers
      [{ list: 'ordered' }, { list: 'bullet' }], // Lists
      [{ indent: '-1' }, { indent: '+1' }], // Indentation
      [{ align: [] }], // Text alignment
      ['link', 'image'], // Links and images
      ['clean'], // Remove formatting
    ],
  };
  UIAnnouncementCategory = UIAnnouncementCategory;
  categoryInfo = CATEGORY_INFO;
  Math = Math; // Make Math available in template

  constructor(
    @Inject(ANNOUNCEMENTS_SERVICE_TOKEN) private announcementsService: AnnouncementsService,
    private fb: FormBuilder,
    private cdr: ChangeDetectorRef,
    private cookieService: CookieService,
    private dialogs: TuiDialogService,
    private alerts: TuiAlertService,
    private translate: TranslateService,
  ) {
    this.pageForm = this.fb.group({
      title: ['', Validators.required],
      content: ['', Validators.required],
    });
  }

  ngOnInit(): void {
    this.loadAnnouncements();
    this.checkNotificationState();
  }

  /**
   * Vérifie l'état de la notification depuis les cookies
   */
  private checkNotificationState(): void {
    const isNotificationClosed = this.cookieService.isAnnouncementsNotificationClosed();
    this.showNotification.set(!isNotificationClosed);
  }

  /**
   * Ferme la notification et mémorise le choix dans un cookie
   */
  closeNotification(): void {
    this.showNotification.set(false);
    this.cookieService.setAnnouncementsNotificationClosed();
  }

  public loadAnnouncements(): void {
    this.announcementsService.getAnnouncementsPaginated(this.paginationParams()).subscribe(response => {
      this.announcements.set(response.announcements);
      this.paginationData.set(response);
      this.cdr.detectChanges(); // Force change detection
    });
  }

  onPostCreate(): void {
    if (this.pageForm.valid) {
      const newAnnouncement = {
        title: this.pageForm.value.title,
        content: this.pageForm.value.content,
        category: this.activePostCategory
      };

      // Show confirmation dialog
      const data: TuiConfirmData = {
        content: this.translate.instant('announcements.messages.confirmCreateContent'),
        yes: this.translate.instant('announcements.messages.confirmCreateYes'),
        no: this.translate.instant('announcements.messages.confirmCreateNo'),
      };

      this.dialogs
        .open<boolean>(TUI_CONFIRM, {
          label: this.translate.instant('announcements.messages.confirmCreateLabel', { title: newAnnouncement.title }),
          size: 'm',
          data,
        })
        .subscribe(response => {
          if (response) {
            this.announcementsService.createAnnouncement(newAnnouncement as any).subscribe({
              next: () => {
                // Reset form and reload announcements
                this.pageForm.reset();
                this.collapsed.set(true);
                this.loadAnnouncements();
                this.alerts.open(
                  this.translate.instant('announcements.messages.createSuccess', { title: newAnnouncement.title }),
                  { appearance: 'positive' }
                ).subscribe();
              },
              error: (error) => {
                console.error('Error creating announcement:', error);
                this.alerts.open(
                  this.translate.instant('announcements.messages.createError'),
                  { appearance: 'error' }
                ).subscribe();
              }
            });
          }
        });
    }
  }

  changePostCategory(category: UIAnnouncementCategory): void {
    this.activePostCategory = category;
  }

  trackByCategory(index: number, item: CategoryInfo): UIAnnouncementCategory {
    return item.category;
  }

  onInputFocus(): void {
    this.collapsed.set(false);
  }

  // Pagination methods
  onPageChange(page: number): void {
    this.paginationParams.update(params => ({ ...params, page }));
    this.loadAnnouncements();
  }

  onPageSizeChange(pageSize: number): void {
    this.paginationParams.update(params => ({ ...params, pageSize, page: 1 }));
    this.loadAnnouncements();
  }

}
