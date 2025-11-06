import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiDataList, TuiIcon, TuiLabel, TuiLoader, TuiTextfield, TuiTextfieldDropdownDirective } from '@taiga-ui/core';
import { TuiCheckbox, TuiChevron, TuiDataListWrapper, TuiInputNumber, TuiInputTime, TuiSelect, TuiSwitch, TuiTextarea } from '@taiga-ui/kit';

import { inject } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { UIDigitalLock } from '../../../../models/UIDigitalLock';
import { UISpace } from '../../../../models/UISpace';
import { ADMIN_SPACES_SERVICE_TOKEN, AdminSpacesService } from '../../admin.providers';
import { AdminDigitalLockService, DIGITAL_LOCK_SERVICE_TOKEN } from '../../services/digital-lock.service';

@Component({
    selector: 'app-spaces-edit',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        ReactiveFormsModule,
        TranslateModule,
        TuiButton,
        TuiIcon,
        TuiLabel,
        TuiLoader,
        TuiDataList,
        TuiTextfield,
        TuiTextfieldDropdownDirective,
        TuiSelect,
        TuiChevron,
        TuiTextarea,
        TuiInputNumber,
        TuiInputTime,
        TuiCheckbox,
        TuiSwitch,
        TuiDataListWrapper
    ],
    providers: [],
    templateUrl: './spaces-edit.component.html',
    styleUrls: ['./spaces-edit.component.scss']
})
export class SpacesEditComponent implements OnInit {
    private spacesService: AdminSpacesService = inject(ADMIN_SPACES_SERVICE_TOKEN);
    private digitalLockService: AdminDigitalLockService = inject(DIGITAL_LOCK_SERVICE_TOKEN);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private fb = inject(FormBuilder);
    private translate = inject(TranslateService);
    private alertService = inject(TuiAlertService);

    spaceForm!: FormGroup;
    loading = signal(false);
    isEdit = signal(false);
    spaceId = signal<string | null>(null);
    images = signal<any[]>([]);
    uploadingImages = signal(false);
    digitalLocks = signal<UIDigitalLock[]>([]);
    availableSpacesForSharing = signal<UISpace[]>([]);
    draftSpace: UISpace | null = null;
    private digitalLocksLoaded = signal(false);
    private availableSpacesLoaded = signal(false);
    private isPopulatingForm = false;
    private destroy$ = new Subject<void>();
    selectedShareSpaceWithMap: { [id: string]: boolean } = {};

    spaceTypes = [
        { value: 'GUEST_ROOM', label: 'spaces.types.GUEST_ROOM' },
        { value: 'COMMON_ROOM', label: 'spaces.types.COMMON_ROOM' },
        { value: 'COWORKING', label: 'spaces.types.COWORKING' },
        { value: 'PARKING', label: 'spaces.types.PARKING' }
    ];

    dayOptions = [
        { value: 'MONDAY', label: 'spaces.days.MONDAY' },
        { value: 'TUESDAY', label: 'spaces.days.TUESDAY' },
        { value: 'WEDNESDAY', label: 'spaces.days.WEDNESDAY' },
        { value: 'THURSDAY', label: 'spaces.days.THURSDAY' },
        { value: 'FRIDAY', label: 'spaces.days.FRIDAY' },
        { value: 'SATURDAY', label: 'spaces.days.SATURDAY' },
        { value: 'SUNDAY', label: 'spaces.days.SUNDAY' }
    ];

    stringifyDay = (day: string): string => {
        const dayOption = this.dayOptions.find(option => option.value === day);
        return dayOption ? this.translate.instant(dayOption.label) : day;
    };

    stringifyType = (item: any): string => {
        if (!item) return '';
        return item.label ? this.translate.instant(item.label) : '';
    };

    stringifyDigitalLock = (item: any): string => {
        if (!item) return '';
        return item.name ? `${item.name}` : '';
    };

    stringifySpace = (space: UISpace | null): string => {
        if (!space) return '';
        return `${space.name} (${this.translate.instant('spaces.types.' + space.type)})`;
    };

    get spacesByType(): { type: string, label: string, spaces: UISpace[] }[] {
        const all = this.availableSpacesForSharing() ?? [];
        const grouped: { [type: string]: UISpace[] } = {};
        all.forEach(space => {
            if (!grouped[space.type]) grouped[space.type] = [];
            grouped[space.type].push(space);
        });
        return Object.keys(grouped).map(type => ({
            type,
            label: this.translate.instant('spaces.types.' + type),
            spaces: grouped[type].sort((a, b) => a.name.localeCompare(b.name)),
        })).sort((a, b) => a.label.localeCompare(b.label));
    }

    objectKeys = Object.keys;

    ngOnInit(): void {
        this.initializeForm();
        this.setupRouteParams();
        this.loadDigitalLocks();
        this.loadAvailableSpaces();
    }

    private initializeForm(): void {
        this.spaceForm = this.fb.group({
            name: ['', [Validators.required, Validators.minLength(3)]],
            type: ['', Validators.required],
            description: ['', [Validators.required, Validators.minLength(10)]],
            instructions: [''],
            status: [true, Validators.required], // true = ACTIVE, false = INACTIVE
            pricing: this.fb.group({
                tenantPrice: [0, [Validators.required, Validators.min(0)]],
                ownerPrice: [0, [Validators.min(0)]],
                cleaningFee: [0, [Validators.min(0)]],
                deposit: [0, [Validators.min(0)]]
            }),
            quota: this.fb.group({
                max: [0, [Validators.min(0)]],
                used: [0, [Validators.min(0)]]
            }),
            rules: this.fb.group({
                minDurationDays: [1, [Validators.min(1)]],
                maxDurationDays: [1, [Validators.min(1)]],
                maxReservationsPerYear: [12, [Validators.min(1)]],
                allowedDays: [[]],
                allowedHours: this.fb.group({
                    start: ['08:00'],
                    end: ['20:00']
                }),
                cleaningDays: [[]],
                requiresApartmentAccess: [false],
                shareSpaceWith: [[]]
            }),
            userRegulations: this.fb.group({
                generalRules: [''],
                safetyInstructions: [''],
                prohibitedItems: [''],
                additionalTerms: ['']
            }),
            digitalLockId: [null],
            cleaningSettings: this.fb.group({
                cleaningEnabled: [false],
                cleaningEmail: [''],
                cleaningNotificationsEnabled: [false],
                cleaningCalendarEnabled: [false],
                cleaningDaysAfterCheckout: [0, [Validators.min(0)]],
                cleaningHour: ['10:00']
            })
        });

        // Listen to shareSpaceWith changes to update selectedSharedSpaces signal
        const rulesGroup = this.spaceForm.get('rules') as FormGroup;
        rulesGroup.get('shareSpaceWith')?.valueChanges
            .pipe(takeUntil(this.destroy$))
            .subscribe((selectedIds: string[]) => {
                console.log('[shareSpaceWith valueChanges]', { selectedIds, isPopulating: this.isPopulatingForm });
                if (!this.isPopulatingForm) {
                    this.updateSelectedSharedSpaces(selectedIds);
                }
            });
    }

    private loadDigitalLocks(): void {
        console.log('[loadDigitalLocks] CALLED');
        this.digitalLockService.getDigitalLocks(0, 100).subscribe({
            next: (digitalLocks: UIDigitalLock[]) => {
                console.log('[loadDigitalLocks] SUCCESS', digitalLocks);
                this.digitalLocks.set(digitalLocks);
                this.digitalLocksLoaded.set(true);
                this.tryPopulateForm();
            },
            error: (error: any) => {
                console.error('[loadDigitalLocks] ERROR', error);
                this.alertService.open('Erreur lors du chargement des serrures numériques').subscribe();
                this.digitalLocksLoaded.set(true);
                this.tryPopulateForm();
            }
        });
    }

    private loadAvailableSpaces(): void {
        console.log('[loadAvailableSpaces] CALLED');
        this.spacesService.getSpaces(0, 100).subscribe({
            next: (response) => {
                console.log('[loadAvailableSpaces] SUCCESS', response);
                const currentId = this.spaceId();
                this.availableSpacesForSharing.set(response.content.filter(s => s.id !== currentId));
                this.availableSpacesLoaded.set(true);
                this.tryPopulateForm();
            },
            error: (error: any) => {
                console.error('[loadAvailableSpaces] ERROR', error);
                this.alertService.open('Erreur lors du chargement des espaces').subscribe();
                this.availableSpacesLoaded.set(true);
                this.tryPopulateForm();
            }
        });
    }

    private loadSpaceDigitalLock(): void {
        const spaceId = this.spaceId();
        if (!spaceId) return;

        this.spacesService.getSpaceDigitalLock(spaceId).subscribe({
            next: (digitalLock: UIDigitalLock | null) => {
                // Set the current associated digital lock
                if (digitalLock) {
                    // Find the digital lock object from digitalLocks array
                    const digitalLockObject = this.digitalLocks().find(d => d.id === digitalLock.id) || null;
                    this.spaceForm.patchValue({
                        digitalLockId: digitalLockObject
                    });
                }
            },
            error: (error: any) => {
                console.error('Error loading space digital lock:', error);
                // Don't show error alert for this as it's not critical
            }
        });
    }

    private setupRouteParams(): void {
        this.route.params.subscribe(params => {
            const id = params['id'];
            if (id) {
                this.isEdit.set(true);
                this.spaceId.set(id);
                this.loadSpace(id);
            } else {
                this.isEdit.set(false);
            }
        });
    }

    private loadSpace(id: string): void {
        this.loading.set(true);
        this.spacesService.getSpace(id).subscribe({
            next: (space: UISpace) => {
                if (space) {
                    this.draftSpace = space;
                    this.tryPopulateForm();
                }
                this.loading.set(false);
            },
            error: (error: any) => {
                console.error('Error loading space:', error);
                this.alertService.open(
                    this.translate.instant('spaces.admin.errorLoadingSpace')
                ).subscribe();
                this.loading.set(false);
            }
        });
    }

    /**
     * Called after loading digitalLocks/AvailableSpaces/Space
     * Will only patch the form if all are loaded and we are in edit mode.
     */
    private tryPopulateForm(): void {
        console.log('[tryPopulateForm] isEdit:', this.isEdit(), 'draftSpace:', this.draftSpace, 'digitalLocksLoaded:', this.digitalLocksLoaded(), 'availableSpacesLoaded:', this.availableSpacesLoaded(), 'isPopulatingForm:', this.isPopulatingForm);
        if (!this.isEdit() || !this.draftSpace) return;
        if (!this.digitalLocksLoaded() || !this.availableSpacesLoaded()) return;
        // Already populated? (avoid extra patch after route param change)
        if (this.isPopulatingForm) return;
        this.populateForm(this.draftSpace);
    }

    private populateForm(space: UISpace): void {
        console.log('[populateForm] ENTER', space);
        this.isPopulatingForm = true;
        console.log('[populateForm] PATCH START', space);
        // Find the type object from spaceTypes array
        const typeObject = this.spaceTypes.find(t => t.value === space.type) || null;

        // Find the digitalLock object from digitalLocks array
        const digitalLockObject = space.digitalLockId ?
            this.digitalLocks().find(dl => dl.id.toString() === space.digitalLockId?.toString()) || null : null;

        this.spaceForm.patchValue({
            name: space.name,
            type: typeObject,
            description: space.description,
            instructions: space.instructions,
            status: space.status === 'ACTIVE', // Convert to boolean for toggle
            digitalLockId: digitalLockObject,
            pricing: {
                tenantPrice: space.pricing.tenantPrice,
                ownerPrice: space.pricing.ownerPrice || 0,
                cleaningFee: space.pricing.cleaningFee || 0,
                deposit: space.pricing.deposit || 0
            },
            quota: {
                max: space.quota?.max || 0
            },
            rules: {
                minDurationDays: space.rules?.minDurationDays || 1,
                maxDurationDays: space.rules?.maxDurationDays || 1,
                maxReservationsPerYear: space.rules?.maxReservationsPerYear || 12,
                allowedDays: space.rules?.allowedDays || [],
                allowedHours: {
                    start: space.rules?.allowedHours?.start || '08:00',
                    end: space.rules?.allowedHours?.end || '20:00'
                },
                cleaningDays: space.rules?.cleaningDays || [],
                requiresApartmentAccess: space.rules?.requiresApartmentAccess || false,
                shareSpaceWith: [] // VALEMENT: handled by map only
            },
            cleaningSettings: {
                cleaningEnabled: space.cleaningSettings?.cleaningEnabled || false,
                cleaningEmail: space.cleaningSettings?.cleaningEmail || '',
                cleaningNotificationsEnabled: space.cleaningSettings?.cleaningNotificationsEnabled || false,
                cleaningCalendarEnabled: space.cleaningSettings?.cleaningCalendarEnabled || false,
                cleaningDaysAfterCheckout: space.cleaningSettings?.cleaningDaysAfterCheckout || 0,
                cleaningHour: space.cleaningSettings?.cleaningHour || '10:00'
            }
        });
        // Initialise la map pour les checkbox partagés
        const shareList: string[] = space.rules?.shareSpaceWith || [];
        this.selectedShareSpaceWithMap = {};
        (this.availableSpacesForSharing() ?? []).forEach(s => {
            this.selectedShareSpaceWithMap[s.id] = shareList.includes(s.id);
        });
        this.images.set(space.images || []);
        this.isPopulatingForm = false;
        console.log('[populateForm] EXIT');
    }

    onSubmit(): void {
        if (this.spaceForm.valid) {
            this.loading.set(true);
            const formData = this.spaceForm.value;
            // Sync shareSpaceWith depuis la map seulement !
            formData.rules.shareSpaceWith = Object.keys(this.selectedShareSpaceWithMap).filter(
                id => this.selectedShareSpaceWithMap[id]
            );
            if (formData.type && typeof formData.type === 'object') {
                formData.type = formData.type.value;
            }
            if (formData.digitalLockId && typeof formData.digitalLockId === 'object') {
                formData.digitalLockId = formData.digitalLockId.id;
            }
            formData.status = formData.status ? 'ACTIVE' : 'INACTIVE';
            if (this.isEdit()) {
                this.updateSpace(formData);
            } else {
                this.createSpace(formData);
            }
        } else {
            this.markFormGroupTouched();
        }
    }

    private createSpace(spaceData: any): void {
        this.spacesService.createSpace(spaceData).subscribe({
            next: () => {
                this.alertService.open(
                    this.translate.instant('spaces.admin.spaceCreated')
                ).subscribe();
                this.loading.set(false);
                this.router.navigate(['/admin/spaces']);
            },
            error: (error: any) => {
                console.error('Error creating space:', error);
                this.alertService.open(
                    this.translate.instant('spaces.admin.errorCreatingSpace')
                ).subscribe();
                this.loading.set(false);
            }
        });
    }

    private updateSpace(spaceData: any): void {
        const spaceId = this.spaceId();
        if (!spaceId) return;

        this.spacesService.updateSpace(spaceId, spaceData).subscribe({
            next: () => {
                this.alertService.open(
                    this.translate.instant('spaces.admin.spaceUpdated')
                ).subscribe();
                this.loading.set(false);
                this.router.navigate(['/admin/spaces']);
            },
            error: (error: any) => {
                console.error('Error updating space:', error);
                this.alertService.open(
                    this.translate.instant('spaces.admin.errorUpdatingSpace')
                ).subscribe();
                this.loading.set(false);
            }
        });
    }

    private handleDigitalLockAssociation(spaceId: string): void {
        const selectedDigitalLockId = this.spaceForm.get('digitalLockId')?.value;

        this.spacesService.associateSpaceWithDigitalLock(spaceId, selectedDigitalLockId).subscribe({
            next: () => {
                this.alertService.open(
                    this.translate.instant('spaces.admin.spaceUpdated')
                ).subscribe();
                this.loading.set(false);
                this.router.navigate(['/admin/spaces']);
            },
            error: (error: any) => {
                console.error('Error associating digital lock:', error);
                this.alertService.open(
                    this.translate.instant('spaces.admin.errorAssociatingDigitalLock')
                ).subscribe();
                this.loading.set(false);
            }
        });
    }

    private markFormGroupTouched(): void {
        Object.keys(this.spaceForm.controls).forEach(key => {
            const control = this.spaceForm.get(key);
            control?.markAsTouched();

            if (control instanceof FormGroup) {
                Object.keys(control.controls).forEach(nestedKey => {
                    control.get(nestedKey)?.markAsTouched();
                });
            }
        });
    }

    onCancel(): void {
        this.router.navigate(['/admin/spaces']);
    }

    getFieldError(fieldName: string): string | null {
        const field = this.spaceForm.get(fieldName);
        if (field?.errors && field.touched) {
            if (field.errors['required']) {
                return this.translate.instant('spaces.admin.fieldRequired');
            }
            if (field.errors['minlength']) {
                return this.translate.instant('spaces.admin.fieldMinLength', { min: field.errors['minlength'].requiredLength });
            }
            if (field.errors['min']) {
                return this.translate.instant('spaces.admin.fieldMinValue', { min: field.errors['min'].min });
            }
        }
        return null;
    }

    getNestedFieldError(groupName: string, fieldName: string): string | null {
        const group = this.spaceForm.get(groupName) as FormGroup;
        const field = group?.get(fieldName);
        if (field?.errors && field.touched) {
            if (field.errors['required']) {
                return this.translate.instant('spaces.admin.fieldRequired');
            }
            if (field.errors['min']) {
                return this.translate.instant('spaces.admin.fieldMinValue', { min: field.errors['min'].min });
            }
        }
        return null;
    }

    // Image management methods

    removeImage(imageId: string): void {
        this.images.update(current => {
            const updated = current.filter(img => img.id !== imageId);
            // If we removed the primary image, make the first remaining image primary
            if (updated.length > 0 && !updated.some(img => img.isPrimary)) {
                updated[0].isPrimary = true;
            }
            return updated;
        });
    }

    setPrimaryImage(imageId: string): void {
        this.images.update(current =>
            current.map(img => ({
                ...img,
                isPrimary: img.id === imageId
            }))
        );
    }

    moveImageUp(imageId: string): void {
        this.images.update(current => {
            const index = current.findIndex(img => img.id === imageId);
            if (index > 0) {
                const newImages = [...current];
                [newImages[index - 1], newImages[index]] = [newImages[index], newImages[index - 1]];
                return newImages;
            }
            return current;
        });
    }

    moveImageDown(imageId: string): void {
        this.images.update(current => {
            const index = current.findIndex(img => img.id === imageId);
            if (index < current.length - 1) {
                const newImages = [...current];
                [newImages[index], newImages[index + 1]] = [newImages[index + 1], newImages[index]];
                return newImages;
            }
            return current;
        });
    }

    onImageUpload(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (input.files && input.files.length > 0) {
            this.uploadingImages.set(true);

            // Mock upload - in real implementation, this would upload to server
            Array.from(input.files).forEach((file, index) => {
                const reader = new FileReader();
                reader.onload = (e) => {
                    const newImage = {
                        id: `img-${Date.now()}-${index}`,
                        url: e.target?.result as string,
                        altText: file.name,
                        isPrimary: this.images().length === 0 // First image is primary
                    };

                    this.images.update(current => [...current, newImage]);

                    if (index === input.files!.length - 1) {
                        this.uploadingImages.set(false);
                    }
                };
                reader.readAsDataURL(file);
            });
        }
    }

    // Checkbox helpers for allowedDays and cleaningDays
    isDaySelected(fieldName: 'allowedDays' | 'cleaningDays', dayValue: string): boolean {
        const rulesGroup = this.spaceForm.get('rules') as FormGroup;
        const currentValues = rulesGroup.get(fieldName)?.value || [];
        return currentValues.includes(dayValue);
    }

    toggleDay(fieldName: 'allowedDays' | 'cleaningDays', dayValue: string): void {
        const rulesGroup = this.spaceForm.get('rules') as FormGroup;
        const currentValues = rulesGroup.get(fieldName)?.value || [];

        if (currentValues.includes(dayValue)) {
            // Remove the day
            const newValues = currentValues.filter((v: string) => v !== dayValue);
            rulesGroup.get(fieldName)?.setValue(newValues);
        } else {
            // Add the day
            rulesGroup.get(fieldName)?.setValue([...currentValues, dayValue]);
        }
    }

    onShareSpaceWithChanged(evt: Event, spaceId: string) {
        const checked = (evt.target as HTMLInputElement)?.checked;
        if (checked && !this.selectedShareSpaceWithMap[spaceId]) {
            this.selectedShareSpaceWithMap[spaceId] = true;
        } else if (!checked && this.selectedShareSpaceWithMap[spaceId]) {
            this.selectedShareSpaceWithMap[spaceId] = false;
        }
    }

    private updateSelectedSharedSpaces(selectedIds: string[]): void {
        console.log('[updateSelectedSharedSpaces] selectedIds:', selectedIds);
        const rulesGroup = this.spaceForm.get('rules') as FormGroup;
        const currentSelectedIds = rulesGroup.get('shareSpaceWith')?.value || [];
        const newSelectedIds = [...currentSelectedIds];

        // Add new selected spaces
        selectedIds.forEach(id => {
            if (!newSelectedIds.includes(id)) {
                newSelectedIds.push(id);
            }
        });

        // Remove deselected spaces
        newSelectedIds.filter(id => !selectedIds.includes(id));

        rulesGroup.get('shareSpaceWith')?.setValue(newSelectedIds);
        console.log('[updateSelectedSharedSpaces] newSelectedIds:', newSelectedIds);
    }
}