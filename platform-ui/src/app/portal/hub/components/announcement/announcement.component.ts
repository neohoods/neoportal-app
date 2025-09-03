import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Inject, Input, Output } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiDataList, TuiDialogService, TuiDropdown, TuiIcon, TuiLabel, TuiTextfield } from '@taiga-ui/core';
import { TUI_CONFIRM, TuiConfirmData } from '@taiga-ui/kit';
import { QuillModule } from 'ngx-quill';
import { getGlobalProviders } from '../../../../global.provider';
import { CATEGORY_COLORS, CATEGORY_ICONS, UIAnnouncement, UIAnnouncementCategory } from '../../../../models/UIAnnoncements';
import { ANNOUNCEMENTS_SERVICE_TOKEN } from '../../hub.provider';
import { AnnouncementsService } from '../../services/annoncements.service';

@Component({
  selector: 'announcement',
  templateUrl: './announcement.component.html',
  styleUrl: './announcement.component.scss',
  imports: [CommonModule, TuiIcon, TuiButton, TuiDataList, TuiDropdown, TranslateModule,
    FormsModule, ReactiveFormsModule, TuiTextfield, TuiLabel, QuillModule],
  providers: [
    ...getGlobalProviders()
  ]
})
export class AnnouncementComponent {
  protected readonly items = ['Edit', 'Delete'];

  @Input({ required: true }) announcement!: UIAnnouncement;
  @Output() announcementDeleted = new EventEmitter<void>();
  @Output() announcementUpdated = new EventEmitter<void>();

  protected open = false;
  protected isEditing = false;
  editForm: FormGroup;

  editorConfig = {
    toolbar: [
      ['bold', 'italic', 'underline'],
      [{ list: 'ordered' }, { list: 'bullet' }],
      ['link'],
      ['clean'],
    ],
  };

  constructor(
    @Inject(ANNOUNCEMENTS_SERVICE_TOKEN) private announcementsService: AnnouncementsService,
    private alertService: TuiAlertService,
    private translateService: TranslateService,
    private dialogs: TuiDialogService,
    private fb: FormBuilder,
  ) {
    this.editForm = this.fb.group({
      title: ['', Validators.required],
      content: ['', Validators.required],
    });
  }

  getIconForCategory(category: UIAnnouncementCategory): string {
    return CATEGORY_ICONS[category] || '@tui.message-circle';
  }

  getColorForCategory(category: UIAnnouncementCategory): string {
    return CATEGORY_COLORS[category] || '#6b7280';
  }

  protected onEdit(): void {
    this.open = false;
    this.isEditing = true;

    // Populate form with current announcement data
    this.editForm.patchValue({
      title: this.announcement.title,
      content: this.announcement.content
    });
  }

  protected onCancelEdit(): void {
    this.isEditing = false;
    this.editForm.reset();
  }

  protected onSaveEdit(): void {
    if (this.editForm.valid) {
      const updatedAnnouncement = {
        ...this.announcement,
        title: this.editForm.value.title,
        content: this.editForm.value.content
      };

      this.announcementsService.updateAnnouncement(this.announcement.id, updatedAnnouncement).subscribe({
        next: () => {
          this.alertService.open(
            this.translateService.instant('announcement.updateSuccess'),
            { appearance: 'success' }
          ).subscribe();
          this.isEditing = false;
          this.announcementUpdated.emit();
        },
        error: (error) => {
          console.error('Error updating announcement:', error);
          this.alertService.open(
            this.translateService.instant('announcement.updateError'),
            { appearance: 'error' }
          ).subscribe();
        }
      });
    }
  }

  protected onDelete(): void {
    this.open = false;

    const data: TuiConfirmData = {
      content: this.translateService.instant('announcement.confirmDelete'),
      yes: this.translateService.instant('announcement.confirmYes'),
      no: this.translateService.instant('announcement.confirmNo'),
    };

    this.dialogs
      .open<boolean>(TUI_CONFIRM, {
        label: this.translateService.instant('announcement.deleteTitle'),
        size: 'm',
        data,
      })
      .subscribe((confirmed: boolean) => {
        if (confirmed) {
          this.announcementsService.deleteAnnouncement(this.announcement.id).subscribe({
            next: () => {
              this.alertService.open(
                this.translateService.instant('announcement.deleteSuccess'),
                { appearance: 'success' }
              ).subscribe();
              this.announcementDeleted.emit();
            },
            error: (error) => {
              console.error('Error deleting announcement:', error);
              this.alertService.open(
                this.translateService.instant('announcement.deleteError'),
                { appearance: 'error' }
              ).subscribe();
            }
          });
        }
      });
  }
}
