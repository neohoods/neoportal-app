import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiLabel, TuiLoader, TuiTextfield } from '@taiga-ui/core';
import { UNITS_SERVICE_TOKEN } from '../../../admin.providers';
import { UnitsService } from '../../../services/units.service';

@Component({
  selector: 'app-unit-edit-admin',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    TranslateModule,
    TuiButton,
    TuiIcon,
    TuiLabel,
    TuiLoader,
    TuiTextfield
  ],
  templateUrl: './unit-edit-admin.component.html',
  styleUrl: './unit-edit-admin.component.scss'
})
export class UnitEditAdminComponent implements OnInit {
  unitForm: FormGroup;
  loading = signal(false);
  saving = signal(false);
  isEditMode = signal(false);
  unitId = signal<string | null>(null);

  constructor(
    @Inject(UNITS_SERVICE_TOKEN) private unitsService: UnitsService,
    private route: ActivatedRoute,
    private router: Router,
    private fb: FormBuilder,
    private translate: TranslateService,
    private alerts: TuiAlertService
  ) {
    this.unitForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(1)]]
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    
    if (id && id !== 'add') {
      this.isEditMode.set(true);
      this.unitId.set(id);
      this.loadUnit(id);
    } else {
      this.isEditMode.set(false);
      this.loading.set(false);
    }
  }

  loadUnit(id: string): void {
    this.loading.set(true);
    this.unitsService.getUnit(id).subscribe({
      next: (unit) => {
        this.unitForm.patchValue({
          name: unit.name || ''
        });
        this.loading.set(false);
      },
      error: (error) => {
        console.error('Failed to load unit:', error);
        this.alerts.open(this.translate.instant('units.loadError')).subscribe();
        this.loading.set(false);
        this.router.navigate(['/admin/units']);
      }
    });
  }

  onSubmit(): void {
    if (this.unitForm.invalid) {
      return;
    }

    this.saving.set(true);
    const name = this.unitForm.value.name;

    if (this.isEditMode() && this.unitId()) {
      // Update existing unit
      this.unitsService.updateUnit(this.unitId()!, name).subscribe({
        next: () => {
          this.alerts.open(this.translate.instant('units.updated', { name })).subscribe();
          this.router.navigate(['/admin/units', this.unitId()]);
        },
        error: (error) => {
          console.error('Failed to update unit:', error);
          this.alerts.open(this.translate.instant('units.updateError')).subscribe();
          this.saving.set(false);
        }
      });
    } else {
      // Create new unit
      this.unitsService.createUnit(name).subscribe({
        next: (unit) => {
          this.alerts.open(this.translate.instant('units.created', { name })).subscribe();
          this.router.navigate(['/admin/units', unit.id]);
        },
        error: (error) => {
          console.error('Failed to create unit:', error);
          this.alerts.open(this.translate.instant('units.createError')).subscribe();
          this.saving.set(false);
        }
      });
    }
  }

  onCancel(): void {
    if (this.isEditMode() && this.unitId()) {
      this.router.navigate(['/admin/units', this.unitId()]);
    } else {
      this.router.navigate(['/admin/units']);
    }
  }
}

