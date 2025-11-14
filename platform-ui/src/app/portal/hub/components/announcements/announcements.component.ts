import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, Inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiDialogService, TuiExpand, TuiIcon, TuiLabel, TuiNotification, TuiTextfield } from '@taiga-ui/core';
import { TUI_CONFIRM, TuiConfirmData, TuiPagination, TuiTabs } from '@taiga-ui/kit';
import {
  TuiInputColorModule
} from '@taiga-ui/legacy';
import { QuillModule } from 'ngx-quill';
import { Subscription } from 'rxjs';
import { distinctUntilChanged, filter } from 'rxjs/operators';
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
export class AnnouncementsComponent implements OnInit, OnDestroy {
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
  private queryParamsSubscription?: Subscription;
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
    private route: ActivatedRoute,
    private router: Router,
  ) {
    this.pageForm = this.fb.group({
      title: ['', Validators.required],
      content: ['', Validators.required],
    });
  }

  ngOnInit(): void {
    this.loadAnnouncements();
    this.checkNotificationState();
    this.setupQueryParamsSubscription();
  }

  ngOnDestroy(): void {
    if (this.queryParamsSubscription) {
      this.queryParamsSubscription.unsubscribe();
    }
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

      // Check if we need to scroll to a specific announcement after loading
      this.scrollToAnnouncementIfNeeded();
    });
  }

  /**
   * Sets up subscription to query params to detect changes even when already on the page
   */
  private setupQueryParamsSubscription(): void {
    this.queryParamsSubscription = this.route.queryParams
      .pipe(
        distinctUntilChanged((prev, curr) => prev['announcementId'] === curr['announcementId']),
        filter(params => !!params['announcementId'])
      )
      .subscribe(params => {
        const announcementId = params['announcementId'];
        if (announcementId) {
          // Wait a bit for announcements to load if they haven't yet
          if (this.announcements().length === 0) {
            // If no announcements loaded yet, wait for them
            setTimeout(() => this.scrollToAnnouncement(announcementId), 500);
          } else {
            this.scrollToAnnouncement(announcementId);
          }
        }
      });
  }

  /**
   * Scrolls to a specific announcement if announcementId is in query params
   */
  private scrollToAnnouncementIfNeeded(): void {
    const announcementId = this.route.snapshot.queryParams['announcementId'];
    if (announcementId) {
      this.scrollToAnnouncement(announcementId);
    }
  }

  /**
   * Scrolls to a specific announcement by ID
   */
  private scrollToAnnouncement(announcementId: string): void {
    // Check if announcement is in current page
    const announcement = this.announcements().find(a => a.id === announcementId);
    if (announcement) {
      // Use setTimeout to ensure DOM is updated
      setTimeout(() => {
        const element = document.getElementById(`announcement-${announcementId}`);
        if (element) {
          // Scroll with smooth behavior, with offset for mobile header
          const headerOffset = 80; // Adjust based on your header height
          const elementPosition = element.getBoundingClientRect().top;
          const offsetPosition = elementPosition + window.pageYOffset - headerOffset;

          window.scrollTo({
            top: offsetPosition,
            behavior: 'smooth'
          });

          // Remove query parameter after scrolling
          this.router.navigate([], {
            relativeTo: this.route,
            queryParams: {},
            replaceUrl: true
          });
        }
      }, 300); // Increased timeout to ensure DOM is fully rendered
    }
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
