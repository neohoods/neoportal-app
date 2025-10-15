import { Observable } from 'rxjs';
import { PlatformFeeSettings } from '../../../api-client';

export abstract class SpaceSettingsService {
    abstract getSpaceSettings(): Observable<PlatformFeeSettings>;
    abstract saveSpaceSettings(settings: PlatformFeeSettings): Observable<PlatformFeeSettings>;
}
