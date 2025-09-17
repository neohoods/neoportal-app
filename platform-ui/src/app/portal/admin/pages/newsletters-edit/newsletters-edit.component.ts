import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import {
    TuiAlertService,
    TuiButton,
    TuiCalendar,
    TuiIcon,
    TuiLabel,
    TuiLoader,
    TuiTextfield
} from '@taiga-ui/core';
import {
    TuiChevron,
    TuiDataListWrapper,
    TuiFilterByInputPipe,
    TuiHideSelectedPipe,
    TuiInputChip,
    TuiInputDateTime,
    TuiMultiSelect
} from '@taiga-ui/kit';
import { TuiInputModule, TuiSelectModule } from '@taiga-ui/legacy';
import { QuillModule } from 'ngx-quill';
import { CreateNewsletterRequest, NewsletterStatus, UINewsletter, UINewsletterAudience, UpdateNewsletterRequest } from '../../../../models/UINewsletter';
import { NEWSLETTERS_SERVICE_TOKEN, USERS_SERVICE_TOKEN } from '../../admin.providers';
import { NewslettersService } from '../../services/newsletters.service';
import { UsersService } from '../../services/users.service';

@Component({
    standalone: true,
    imports: [
        FormsModule,
        CommonModule,
        ReactiveFormsModule,
        RouterModule,
        TranslateModule,
        TuiButton,
        TuiIcon,
        TuiLabel,
        TuiTextfield,
        TuiInputModule,
        TuiSelectModule,
        TuiCalendar,
        TuiChevron,
        TuiInputChip,
        TuiInputDateTime,
        TuiDataListWrapper,
        TuiFilterByInputPipe,
        TuiHideSelectedPipe,
        TuiMultiSelect,
        TuiLoader,
        QuillModule
    ],
    selector: 'app-newsletters-edit',
    templateUrl: './newsletters-edit.component.html',
    styleUrls: ['./newsletters-edit.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NewslettersEditComponent implements OnInit {
    newsletterForm: FormGroup;
    isEditMode = false;
    newsletterId: string | null = null;
    newsletter: UINewsletter | null = null;
    isLoading = false;

    audienceTypes = [
        { value: 'ALL', label: 'newsletters.audience.all' },
        { value: 'USER_TYPES', label: 'newsletters.audience.userTypes' },
        { value: 'SPECIFIC_USERS', label: 'newsletters.audience.specificUsers' }
    ];

    userTypes = ['OWNER', 'TENANT', 'ADMIN'];
    selectedUserTypes: string[] = [];
    selectedUserIds: string[] = [];

    availableUsers: any[] = []; // Will be loaded from API

    // Stringify functions for selects
    stringifyAudienceType = (item: any) => {
        if (!item || !item.label) return '';
        return this.translate.instant(item.label);
    };

    stringifyUserType = (userType: string): string => {
        return this.translate.instant(`newsletters.userTypes.${userType.toLowerCase()}`);
    };

    stringifyUser = (user: any): string => {
        return user ? `${user.firstName} ${user.lastName}` : '';
    };

    editorConfig = {
        toolbar: [
            ['bold', 'italic', 'underline', 'strike'], // Text formatting
            [{ header: 1 }, { header: 2 }, { header: 3 }], // Headers
            [{ list: 'ordered' }, { list: 'bullet' }], // Lists
            [{ indent: '-1' }, { indent: '+1' }], // Indentation
            [{ align: [] }], // Text alignment
            ['link', 'image'], // Links and images
            ['blockquote', 'code-block'], // Block elements
            [{ color: [] }, { background: [] }], // Text colors
            ['clean'], // Remove formatting
        ]
        // Removed theme: 'snow' to avoid module loading issues
    };

    constructor(
        private fb: FormBuilder,
        private router: Router,
        private route: ActivatedRoute,
        private alerts: TuiAlertService,
        @Inject(NEWSLETTERS_SERVICE_TOKEN) private newslettersService: NewslettersService,
        @Inject(USERS_SERVICE_TOKEN) private usersService: UsersService,
        private translate: TranslateService
    ) {
        this.newsletterForm = this.fb.group({
            subject: ['', [Validators.required, Validators.maxLength(255)]],
            content: ['', [Validators.required]],
            audienceType: [this.audienceTypes[0], [Validators.required]], // Initialize with the first object
            scheduledAt: [null], // Optional scheduled date
        });
    }

    ngOnInit(): void {
        this.newsletterId = this.route.snapshot.paramMap.get('id');
        this.isEditMode = !!this.newsletterId;

        // Load available users for specific user selection
        this.loadAvailableUsers();

        if (this.isEditMode && this.newsletterId) {
            this.loadNewsletter(this.newsletterId);
        }
    }

    private loadAvailableUsers(): void {
        this.usersService.getUsers().subscribe({
            next: (users) => {
                this.availableUsers = users.map(user => ({
                    id: user.id,
                    firstName: user.firstName,
                    lastName: user.lastName,
                    email: user.email
                }));
            },
            error: (error) => {
                console.error('Error loading users:', error);
                // Fallback to empty array if API fails
                this.availableUsers = [];
            }
        });
    }

    private loadNewsletter(id: string): void {
        this.isLoading = true;
        this.newslettersService.getNewsletter(id).subscribe({
            next: (newsletter) => {
                this.newsletter = newsletter;
                // Find the audience type object
                const audienceTypeValue = newsletter.audience?.type || 'ALL';
                const audienceTypeObject = this.audienceTypes.find(type => type.value === audienceTypeValue) || this.audienceTypes[0];

                this.newsletterForm.patchValue({
                    subject: newsletter.subject,
                    content: newsletter.content,
                    audienceType: audienceTypeObject,
                });

                // Set the selected values for the chips
                this.selectedUserTypes = newsletter.audience?.userTypes || [];
                this.selectedUserIds = newsletter.audience?.userIds || [];

                this.isLoading = false;
            },
            error: (error) => {
                console.error('Error loading newsletter:', error);
                this.alerts.open(
                    this.translate.instant('newsletters.messages.updateError'),
                    { appearance: 'error' }
                ).subscribe();
                this.isLoading = false;
                this.router.navigate(['/admin/newsletters']);
            }
        });
    }

    onSubmit(): void {
        if (this.newsletterForm.valid) {
            const formValue = this.newsletterForm.value;

            if (this.isEditMode && this.newsletterId) {
                this.updateNewsletter(this.newsletterId, formValue);
            } else {
                this.createNewsletter(formValue);
            }
        } else {
            this.markFormGroupTouched(this.newsletterForm);
        }
    }

    private createNewsletter(formValue: any): void {
        this.isLoading = true;
        const audience: UINewsletterAudience = {
            type: formValue.audienceType?.value || formValue.audienceType,
            userTypes: formValue.audienceType?.value === 'USER_TYPES' || formValue.audienceType === 'USER_TYPES'
                ? this.selectedUserTypes
                : undefined,
            userIds: formValue.audienceType?.value === 'SPECIFIC_USERS' || formValue.audienceType === 'SPECIFIC_USERS'
                ? this.selectedUserIds
                : undefined
        };

        const request: CreateNewsletterRequest = {
            subject: formValue.subject,
            content: formValue.content,
            audience: audience
        };

        this.newslettersService.createNewsletter(request).subscribe({
            next: () => {
                this.alerts.open(
                    this.translate.instant('newsletters.messages.createSuccess'),
                    { appearance: 'positive' }
                ).subscribe();
                this.router.navigate(['/admin/newsletters']);
            },
            error: (error) => {
                console.error('Error creating newsletter:', error);
                this.alerts.open(
                    this.translate.instant('newsletters.messages.createError'),
                    { appearance: 'error' }
                ).subscribe();
                this.isLoading = false;
            }
        });
    }

    private updateNewsletter(id: string, formValue: any): void {
        this.isLoading = true;
        const audience: UINewsletterAudience = {
            type: formValue.audienceType?.value || formValue.audienceType,
            userTypes: formValue.audienceType?.value === 'USER_TYPES' || formValue.audienceType === 'USER_TYPES'
                ? this.selectedUserTypes
                : undefined,
            userIds: formValue.audienceType?.value === 'SPECIFIC_USERS' || formValue.audienceType === 'SPECIFIC_USERS'
                ? this.selectedUserIds
                : undefined
        };

        const request: UpdateNewsletterRequest = {
            subject: formValue.subject,
            content: formValue.content,
            audience: audience
        };

        this.newslettersService.updateNewsletter(id, request).subscribe({
            next: () => {
                this.alerts.open(
                    this.translate.instant('newsletters.messages.updateSuccess'),
                    { appearance: 'positive' }
                ).subscribe();
                this.router.navigate(['/admin/newsletters']);
            },
            error: (error) => {
                console.error('Error updating newsletter:', error);
                this.alerts.open(
                    this.translate.instant('newsletters.messages.updateError'),
                    { appearance: 'error' }
                ).subscribe();
                this.isLoading = false;
            }
        });
    }

    onCancel(): void {
        this.router.navigate(['/admin/newsletters']);
    }

    getUserTypeLabel = (userType: string): string => {
        return this.translate.instant(`newsletters.userTypes.${userType.toLowerCase()}`);
    }

    getUserDisplayName = (user: any): string => {
        if (!user || !user.firstName || !user.lastName || !user.email) return '';
        return `${user.firstName} ${user.lastName} (${user.email})`;
    }

    private markFormGroupTouched(formGroup: FormGroup): void {
        Object.keys(formGroup.controls).forEach(key => {
            const control = formGroup.get(key);
            control?.markAsTouched();
        });
    }

    get pageTitle(): string {
        return this.isEditMode
            ? this.translate.instant('newsletters.form.edit.title')
            : this.translate.instant('newsletters.form.create.title');
    }

    get pageDescription(): string {
        return this.isEditMode
            ? this.translate.instant('newsletters.form.edit.description')
            : this.translate.instant('newsletters.form.create.description');
    }

    get canEdit(): boolean {
        return !this.isEditMode || (this.newsletter?.status === NewsletterStatus.DRAFT);
    }

    onTestNewsletter(): void {
        if (this.newsletterForm.valid) {
            const formValue = this.newsletterForm.value;

            // Si on est en mode édition, on teste la newsletter existante
            if (this.isEditMode && this.newsletterId) {
                this.testNewsletter(this.newsletterId);
            } else {
                // Si on est en mode création, on doit d'abord créer la newsletter en mode brouillon
                this.createNewsletterForTest(formValue);
            }
        } else {
            this.markFormGroupTouched(this.newsletterForm);
        }
    }

    private testNewsletter(newsletterId: string): void {
        this.isLoading = true;
        this.newslettersService.testNewsletter(newsletterId).subscribe({
            next: () => {
                this.alerts.open(
                    this.translate.instant('newsletters.messages.testSuccess'),
                    { appearance: 'positive' }
                ).subscribe();
                this.isLoading = false;
            },
            error: (error) => {
                console.error('Error testing newsletter:', error);
                this.alerts.open(
                    this.translate.instant('newsletters.messages.testError'),
                    { appearance: 'error' }
                ).subscribe();
                this.isLoading = false;
            }
        });
    }

    private createNewsletterForTest(formValue: any): void {
        this.isLoading = true;
        const audience: UINewsletterAudience = {
            type: formValue.audienceType?.value || formValue.audienceType,
            userTypes: formValue.audienceType?.value === 'USER_TYPES' || formValue.audienceType === 'USER_TYPES'
                ? this.selectedUserTypes
                : undefined,
            userIds: formValue.audienceType?.value === 'SPECIFIC_USERS' || formValue.audienceType === 'SPECIFIC_USERS'
                ? this.selectedUserIds
                : undefined
        };

        const request: CreateNewsletterRequest = {
            subject: formValue.subject,
            content: formValue.content,
            audience: audience
        };

        this.newslettersService.createNewsletter(request).subscribe({
            next: (newsletter) => {
                // Maintenant on peut tester la newsletter créée
                this.testNewsletter(newsletter.id);
                // Mettre à jour le mode pour refléter qu'on a maintenant une newsletter créée
                this.isEditMode = true;
                this.newsletterId = newsletter.id;
                this.newsletter = newsletter;
            },
            error: (error) => {
                console.error('Error creating newsletter for test:', error);
                this.alerts.open(
                    this.translate.instant('newsletters.messages.createError'),
                    { appearance: 'error' }
                ).subscribe();
                this.isLoading = false;
            }
        });
    }
}