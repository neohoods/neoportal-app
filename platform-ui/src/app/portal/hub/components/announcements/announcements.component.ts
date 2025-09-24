import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, Inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TuiButton, TuiExpand, TuiIcon, TuiLabel, TuiNotification, TuiTextfield } from '@taiga-ui/core';
import { TuiPagination, TuiTabs } from '@taiga-ui/kit';
import {
  TuiInputColorModule
} from '@taiga-ui/legacy';
import { QuillModule } from 'ngx-quill';
import { getGlobalProviders } from '../../../../global.provider';
import { CATEGORY_INFO, CategoryInfo, UIAnnouncement, UIAnnouncementCategory, UIPaginatedAnnouncementsResponse, UIPaginationParams } from '../../../../models/UIAnnoncements';
import { ANNOUNCEMENTS_SERVICE_TOKEN } from '../../hub.provider';
import { AnnouncementsService } from '../../services/annoncements.service';
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
  ) {
    this.pageForm = this.fb.group({
      title: ['', Validators.required],
      content: ['', Validators.required],
    });
  }

  ngOnInit(): void {
    this.loadAnnouncements();
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

      this.announcementsService.createAnnouncement(newAnnouncement).subscribe({
        next: () => {
          // Reset form and reload announcements
          this.pageForm.reset();
          this.collapsed.set(true);
          this.loadAnnouncements();
        },
        error: (error) => {
          console.error('Error creating announcement:', error);
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
