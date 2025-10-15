import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { PlatformFeeSettings } from '../../../../api-client';
import { SpaceSettingsService } from '../space-settings.service';

@Injectable({
    providedIn: 'root',
})
export class MockSpaceSettingsService implements SpaceSettingsService {
    private mockSettings: PlatformFeeSettings = {
        platformFeePercentage: 2.0,
        platformFixedFee: 0.25,
    };

    getSpaceSettings(): Observable<PlatformFeeSettings> {
        return of(this.mockSettings).pipe(delay(300));
    }

    saveSpaceSettings(settings: PlatformFeeSettings): Observable<PlatformFeeSettings> {
        this.mockSettings = { ...settings };
        return of(this.mockSettings).pipe(delay(300));
    }
}
