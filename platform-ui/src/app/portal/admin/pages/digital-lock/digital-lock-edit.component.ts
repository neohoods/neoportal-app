import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiLabel, TuiLoader, TuiTextfield } from '@taiga-ui/core';
import { TuiChevron, TuiDataListWrapper, TuiSelect } from '@taiga-ui/kit';
import { UIDigitalLock } from '../../../../models/UIDigitalLock';
import { DIGITAL_LOCK_SERVICE_TOKEN } from '../../services/digital-lock.service';

@Component({
    selector: 'app-digital-lock-edit',
    standalone: true,
    imports: [
        CommonModule,
        ReactiveFormsModule,
        TuiButton,
        TuiIcon,
        TuiLabel,
        TuiLoader,
        TuiTextfield,
        TuiSelect,
        TuiChevron,
        TuiDataListWrapper,
        TranslateModule
    ],
    templateUrl: './digital-lock-edit.component.html',
    styleUrls: ['./digital-lock-edit.component.scss']
})
export class DigitalLockEditComponent implements OnInit {
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private fb = inject(FormBuilder);
    private translate = inject(TranslateService);
    private alertService = inject(TuiAlertService);
    private digitalLockService = inject(DIGITAL_LOCK_SERVICE_TOKEN);

    digitalLockForm!: FormGroup;
    loading = signal(false);
    saving = signal(false);
    isEditMode = signal(false);
    digitalLockId = signal<string | null>(null);

    statusOptions = [
        { value: 'ACTIVE', label: 'digitalLock.admin.status.ACTIVE' },
        { value: 'INACTIVE', label: 'digitalLock.admin.status.INACTIVE' },
        { value: 'ERROR', label: 'digitalLock.admin.status.ERROR' }
    ];

    typeOptions = [
        { value: 'TTLOCK', label: 'digitalLock.admin.types.TTLOCK' },
        { value: 'NUKI', label: 'digitalLock.admin.types.NUKI' },
        { value: 'YALE', label: 'digitalLock.admin.types.YALE' }
    ];

    stringifyStatus = (item: any): string => {
        if (!item || !item.label) return '';
        return this.translate.instant(item.label);
    };

    stringifyType = (item: any): string => {
        if (!item || !item.label) return '';
        return this.translate.instant(item.label);
    };

    ngOnInit(): void {
        this.initializeForm();
        this.loadDigitalLock();
    }

    private initializeForm(): void {
        this.digitalLockForm = this.fb.group({
            name: ['', [Validators.required, Validators.minLength(2)]],
            type: ['TTLOCK', [Validators.required]],
            status: ['ACTIVE', [Validators.required]],
            config: this.fb.group({
                deviceId: ['', [Validators.required, Validators.minLength(2)]],
                location: ['', [Validators.required, Validators.minLength(2)]],
                batteryLevel: [null],
                signalStrength: [null],
                lastSeen: [null]
            })
        });
    }

    private loadDigitalLock(): void {
        const id = this.route.snapshot.paramMap.get('id');
        if (id && id !== 'add') {
            this.isEditMode.set(true);
            this.digitalLockId.set(id);
            this.loading.set(true);

            this.digitalLockService.getDigitalLockById(id).subscribe({
                next: (digitalLock: UIDigitalLock) => {
                    this.populateForm(digitalLock);
                    this.loading.set(false);
                },
                error: (error) => {
                    console.error('Error loading digital lock:', error);
                    this.loading.set(false);
                    this.alertService.open(
                        this.translate.instant('digitalLock.admin.errorLoadingLock') || 'Error loading digital lock',
                        { appearance: 'error' }
                    ).subscribe();
                }
            });
        }
    }

    private populateForm(digitalLock: UIDigitalLock): void {
        // Find the matching option objects for type and status
        const typeOption = this.typeOptions.find(opt => opt.value === digitalLock.type);
        const statusOption = this.statusOptions.find(opt => opt.value === digitalLock.status);

        // Get config data based on type
        let configData = {};
        if (digitalLock.type === 'TTLOCK' && digitalLock.ttlockConfig) {
            configData = {
                deviceId: digitalLock.ttlockConfig.deviceId,
                location: digitalLock.ttlockConfig.location,
                batteryLevel: digitalLock.ttlockConfig.batteryLevel,
                signalStrength: digitalLock.ttlockConfig.signalStrength,
                lastSeen: digitalLock.ttlockConfig.lastSeen
            };
        } else if (digitalLock.type === 'NUKI' && digitalLock.nukiConfig) {
            configData = {
                deviceId: digitalLock.nukiConfig.deviceId,
                location: 'N/A', // Nuki doesn't have location
                batteryLevel: digitalLock.nukiConfig.batteryLevel,
                signalStrength: null, // Nuki doesn't have signal strength
                lastSeen: digitalLock.nukiConfig.lastSeen
            };
        }

        this.digitalLockForm.patchValue({
            name: digitalLock.name,
            type: typeOption || digitalLock.type,
            status: statusOption || digitalLock.status,
            config: configData
        });
    }

    onSubmit(): void {
        if (this.digitalLockForm.valid) {
            this.saving.set(true);

            const formData = this.digitalLockForm.value;
            const type = typeof formData.type === 'object' ? formData.type.value : formData.type;
            const status = typeof formData.status === 'object' ? formData.status.value : formData.status;

            const digitalLockData: Partial<UIDigitalLock> = {
                name: formData.name,
                type: type,
                status: status
            };

            // Add appropriate config based on type
            if (type === 'TTLOCK') {
                digitalLockData.ttlockConfig = {
                    id: "0", // Will be set by the backend
                    deviceId: formData.config.deviceId,
                    location: formData.config.location,
                    batteryLevel: formData.config.batteryLevel,
                    signalStrength: formData.config.signalStrength,
                    lastSeen: formData.config.lastSeen
                };
            } else if (type === 'NUKI') {
                digitalLockData.nukiConfig = {
                    id: "0", // Will be set by the backend
                    deviceId: formData.config.deviceId,
                    token: '', // Will be set by the backend
                    batteryLevel: formData.config.batteryLevel,
                    lastSeen: formData.config.lastSeen
                };
            }

            if (this.isEditMode()) {
                this.updateDigitalLock(digitalLockData);
            } else {
                this.createDigitalLock(digitalLockData);
            }
        } else {
            this.markFormGroupTouched();
        }
    }

    private createDigitalLock(digitalLockData: Partial<UIDigitalLock>): void {
        this.digitalLockService.createDigitalLock(digitalLockData).subscribe({
            next: (createdDigitalLock) => {
                this.saving.set(false);
                this.alertService.open(
                    this.translate.instant('digitalLock.admin.deviceCreated'),
                    { appearance: 'success' }
                ).subscribe();
                this.router.navigate(['/admin/digital-locks']);
            },
            error: (error) => {
                console.error('Error creating digital lock:', error);
                this.saving.set(false);
                this.alertService.open(
                    this.translate.instant('digitalLock.admin.errorCreatingLock') || 'Error creating digital lock',
                    { appearance: 'error' }
                ).subscribe();
            }
        });
    }

    private updateDigitalLock(digitalLockData: Partial<UIDigitalLock>): void {
        const id = this.digitalLockId();
        if (!id) return;

        this.digitalLockService.updateDigitalLock(id, digitalLockData).subscribe({
            next: (updatedDigitalLock) => {
                this.saving.set(false);
                this.alertService.open(
                    this.translate.instant('digitalLock.admin.deviceUpdated'),
                    { appearance: 'success' }
                ).subscribe();
                this.router.navigate(['/admin/digital-locks']);
            },
            error: (error) => {
                console.error('Error updating digital lock:', error);
                this.saving.set(false);
                this.alertService.open(
                    this.translate.instant('digitalLock.admin.errorUpdatingLock') || 'Error updating digital lock',
                    { appearance: 'error' }
                ).subscribe();
            }
        });
    }

    private markFormGroupTouched(): void {
        Object.keys(this.digitalLockForm.controls).forEach(key => {
            const control = this.digitalLockForm.get(key);
            control?.markAsTouched();

            if (key === 'config') {
                const configGroup = this.digitalLockForm.get('config') as FormGroup;
                Object.keys(configGroup.controls).forEach(configKey => {
                    configGroup.get(configKey)?.markAsTouched();
                });
            }
        });
    }

    onCancel(): void {
        this.router.navigate(['/admin/digital-locks']);
    }

    getFieldError(fieldName: string): string | null {
        const field = this.digitalLockForm.get(fieldName);
        if (field?.errors && field.touched) {
            if (field.errors['required']) {
                return this.translate.instant('digitalLock.admin.fieldRequired');
            }
            if (field.errors['minlength']) {
                return this.translate.instant('digitalLock.admin.fieldMinLength', { min: field.errors['minlength'].requiredLength });
            }
        }
        return null;
    }

    getConfigFieldError(fieldName: string): string | null {
        const configGroup = this.digitalLockForm.get('config') as FormGroup;
        const field = configGroup.get(fieldName);
        if (field?.errors && field.touched) {
            if (field.errors['required']) {
                return this.translate.instant('digitalLock.admin.fieldRequired');
            }
            if (field.errors['minlength']) {
                return this.translate.instant('digitalLock.admin.fieldMinLength', { min: field.errors['minlength'].requiredLength });
            }
        }
        return null;
    }

    getSelectedType(): string {
        const typeValue = this.digitalLockForm.get('type')?.value;
        return typeof typeValue === 'object' ? typeValue.value : typeValue;
    }
}
