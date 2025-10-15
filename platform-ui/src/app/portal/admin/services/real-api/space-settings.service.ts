import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PlatformFeeSettings, SpaceSettingsAdminApiService } from '../../../../api-client';
import { SpaceSettingsService } from '../space-settings.service';

@Injectable({
    providedIn: 'root',
})
export class RealSpaceSettingsService implements SpaceSettingsService {
    constructor(@Inject(SpaceSettingsAdminApiService) private spaceSettingsAdminApi: SpaceSettingsAdminApiService) { }

    getSpaceSettings(): Observable<PlatformFeeSettings> {
        return this.spaceSettingsAdminApi.getSpaceSettings();
    }

    saveSpaceSettings(settings: PlatformFeeSettings): Observable<PlatformFeeSettings> {
        return this.spaceSettingsAdminApi.saveSpaceSettings(settings);
    }
}
