import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiTextfield } from '@taiga-ui/core';
import { TuiInputNumber } from '@taiga-ui/kit';
import { PlatformFeeSettings } from '../../../../api-client';
import { SPACE_SETTINGS_SERVICE_TOKEN } from '../../admin.providers';
import { SpaceSettingsService } from '../../services/space-settings.service';

@Component({
    standalone: true,
    selector: 'app-space-settings',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        TuiButton,
        TuiInputNumber,
        TranslateModule,
        CommonModule,
        TuiTextfield,
    ],
    templateUrl: './space-settings.component.html',
    styleUrl: './space-settings.component.scss',
})
export class SpaceSettingsComponent implements OnInit {
    spaceSettingsForm: FormGroup;

    constructor(
        private formBuilder: FormBuilder,
        @Inject(SPACE_SETTINGS_SERVICE_TOKEN)
        private spaceSettingsService: SpaceSettingsService,
        private alerts: TuiAlertService,
        private translate: TranslateService,
    ) {
        this.spaceSettingsForm = this.formBuilder.group({
            platformFeePercentage: [2.0, [Validators.required, Validators.min(0), Validators.max(100)]],
            platformFixedFee: [0.25, [Validators.required, Validators.min(0)]],
        });
    }

    ngOnInit() {
        this.loadSpaceSettings();
    }

    private loadSpaceSettings() {
        this.spaceSettingsService
            .getSpaceSettings()
            .subscribe((settings) => {
                this.spaceSettingsForm.patchValue(settings);
            });
    }

    onSubmit() {
        if (this.spaceSettingsForm.valid) {
            const formValue = this.spaceSettingsForm.value as PlatformFeeSettings;
            this.spaceSettingsService
                .saveSpaceSettings(formValue)
                .subscribe((savedSettings) => {
                    // Update form with saved values
                    this.spaceSettingsForm.patchValue(savedSettings);

                    // Show success notification
                    this.translate.get('spaces.settings.saveSuccess').subscribe((message: string) => {
                        this.alerts
                            .open(message, {
                                appearance: 'positive',
                            })
                            .subscribe();
                    });
                });
        }
    }

    // Getter for easy template access
    get formControls() {
        return this.spaceSettingsForm.controls;
    }
}
