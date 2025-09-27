import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import {
    TuiAlertService,
    TuiButton,
    TuiIcon,
    TuiLabel,
    TuiLoader,
    TuiTextfield
} from '@taiga-ui/core';
import {
    TuiDataListWrapper,
    TuiFilterByInputPipe,
    TuiHideSelectedPipe,
    TuiSelect
} from '@taiga-ui/kit';
import { TuiInputModule, TuiSelectModule } from '@taiga-ui/legacy';
import { QuillModule } from 'ngx-quill';
import { CreateEmailTemplateRequest, EmailTemplateType, UIEmailTemplate, UpdateEmailTemplateRequest } from '../../../../models/UIEmailTemplate';
import { EMAIL_TEMPLATES_SERVICE_TOKEN } from '../../admin.providers';
import { EmailTemplatesService } from '../../services/email-templates.service';

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
        TuiSelect,
        TuiDataListWrapper,
        TuiFilterByInputPipe,
        TuiHideSelectedPipe,
        TuiLoader,
        QuillModule
    ],
    selector: 'app-email-templates-edit',
    templateUrl: './email-templates-edit.component.html',
    styleUrls: ['./email-templates-edit.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailTemplatesEditComponent implements OnInit {
    protected form: FormGroup;
    protected isEditMode = false;
    protected templateId: string | null = null;
    protected template: UIEmailTemplate | null = null;
    protected isLoading = false;

    // Available template types
    protected templateTypes = [
        { value: EmailTemplateType.WELCOME, label: 'emailTemplates.types.WELCOME' }
    ];

    // Stringify function for select
    protected stringifyTemplateType = (item: any) => {
        if (!item || !item.label) return '';
        return this.translate.instant(item.label);
    };

    // Quill editor configuration
    protected editorConfig = {
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
    };

    constructor(
        private fb: FormBuilder,
        private router: Router,
        private route: ActivatedRoute,
        private alerts: TuiAlertService,
        @Inject(EMAIL_TEMPLATES_SERVICE_TOKEN) private emailTemplatesService: EmailTemplatesService,
        private translate: TranslateService
    ) {
        this.form = this.fb.group({
            type: [this.templateTypes[0], [Validators.required]],
            name: ['', [Validators.required, Validators.minLength(3)]],
            subject: ['', [Validators.required, Validators.minLength(3)]],
            content: ['', [Validators.required]],
            isActive: [false],
            description: ['']
        });
    }

    ngOnInit(): void {
        this.templateId = this.route.snapshot.paramMap.get('id');
        this.isEditMode = !!this.templateId;

        if (this.isEditMode && this.templateId) {
            this.loadTemplate(this.templateId);
        }
    }

    private loadTemplate(id: string): void {
        this.isLoading = true;
        this.emailTemplatesService.getEmailTemplate(id).subscribe({
            next: (template) => {
                this.template = template;
                // Find the template type object
                const templateTypeValue = template.type || EmailTemplateType.WELCOME;
                const templateTypeObject = this.templateTypes.find(type => type.value === templateTypeValue) || this.templateTypes[0];

                this.form.patchValue({
                    type: templateTypeObject,
                    name: template.name,
                    subject: template.subject,
                    content: template.content,
                    isActive: template.isActive,
                    description: template.description
                });

                this.isLoading = false;
            },
            error: (error) => {
                console.error('Error loading email template:', error);
                this.alerts.open(
                    this.translate.instant('emailTemplates.errors.loadError')
                ).subscribe();
                this.isLoading = false;
                this.router.navigate(['/admin/email-templates']);
            }
        });
    }

    protected onSubmit(): void {
        if (this.form.valid) {
            const formValue = this.form.value;

            if (this.isEditMode && this.templateId) {
                this.updateTemplate(this.templateId, formValue);
            } else {
                this.createTemplate(formValue);
            }
        } else {
            this.markFormGroupTouched(this.form);
        }
    }

    private createTemplate(formValue: any): void {
        this.isLoading = true;
        const request: CreateEmailTemplateRequest = {
            type: formValue.type?.value || formValue.type,
            name: formValue.name,
            subject: formValue.subject,
            content: formValue.content,
            isActive: formValue.isActive,
            description: formValue.description
        };

        this.emailTemplatesService.createEmailTemplate(request).subscribe({
            next: () => {
                this.alerts.open(
                    this.translate.instant('emailTemplates.actions.createSuccess')
                ).subscribe();
                this.router.navigate(['/admin/email-templates']);
            },
            error: (error) => {
                console.error('Error creating email template:', error);
                this.alerts.open(
                    this.translate.instant('emailTemplates.actions.createError')
                ).subscribe();
                this.isLoading = false;
            }
        });
    }

    private updateTemplate(id: string, formValue: any): void {
        this.isLoading = true;
        const request: UpdateEmailTemplateRequest = {
            type: formValue.type?.value || formValue.type,
            name: formValue.name,
            subject: formValue.subject,
            content: formValue.content,
            isActive: formValue.isActive,
            description: formValue.description
        };

        this.emailTemplatesService.updateEmailTemplate(id, request).subscribe({
            next: () => {
                this.alerts.open(
                    this.translate.instant('emailTemplates.actions.updateSuccess')
                ).subscribe();
                this.router.navigate(['/admin/email-templates']);
            },
            error: (error) => {
                console.error('Error updating email template:', error);
                this.alerts.open(
                    this.translate.instant('emailTemplates.actions.updateError')
                ).subscribe();
                this.isLoading = false;
            }
        });
    }

    protected onCancel(): void {
        this.router.navigate(['/admin/email-templates']);
    }

    protected testTemplate(): void {
        if (this.form.valid) {
            // Si on est en mode édition, on teste le template existant
            if (this.isEditMode && this.templateId) {
                this.testExistingTemplate(this.templateId);
            } else {
                // Si on est en mode création, on doit d'abord créer le template
                this.createTemplateForTest(this.form.value);
            }
        } else {
            this.markFormGroupTouched(this.form);
        }
    }

    private testExistingTemplate(templateId: string): void {
        this.isLoading = true;
        this.emailTemplatesService.testEmailTemplate(templateId).subscribe({
            next: () => {
                this.alerts.open(
                    this.translate.instant('emailTemplates.actions.testSuccess')
                ).subscribe();
                this.isLoading = false;
            },
            error: (error) => {
                console.error('Error testing email template:', error);
                this.alerts.open(
                    this.translate.instant('emailTemplates.actions.testError')
                ).subscribe();
                this.isLoading = false;
            }
        });
    }

    private createTemplateForTest(formValue: any): void {
        this.isLoading = true;
        const request: CreateEmailTemplateRequest = {
            type: formValue.type?.value || formValue.type,
            name: formValue.name,
            subject: formValue.subject,
            content: formValue.content,
            isActive: formValue.isActive,
            description: formValue.description
        };

        this.emailTemplatesService.createEmailTemplate(request).subscribe({
            next: (template) => {
                // Maintenant on peut tester le template créé
                this.testExistingTemplate(template.id);
                // Mettre à jour le mode pour refléter qu'on a maintenant un template créé
                this.isEditMode = true;
                this.templateId = template.id;
                this.template = template;
            },
            error: (error) => {
                console.error('Error creating email template for test:', error);
                this.alerts.open(
                    this.translate.instant('emailTemplates.actions.createError')
                ).subscribe();
                this.isLoading = false;
            }
        });
    }

    private markFormGroupTouched(formGroup: FormGroup): void {
        Object.keys(formGroup.controls).forEach(key => {
            const control = formGroup.get(key);
            control?.markAsTouched();
        });
    }

    protected get pageTitle(): string {
        return this.isEditMode
            ? this.translate.instant('emailTemplates.edit.title')
            : this.translate.instant('emailTemplates.create.title');
    }

    protected get pageDescription(): string {
        return this.isEditMode
            ? this.translate.instant('emailTemplates.edit.description')
            : this.translate.instant('emailTemplates.create.description');
    }

    protected get canEdit(): boolean {
        return true; // Email templates can always be edited
    }
}