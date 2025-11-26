import { Observable } from 'rxjs';
import { GetMatrixBotDeviceCode200Response } from '../../../api-client/model/getMatrixBotDeviceCode200Response';
import { GetMatrixBotOAuth2RedirectUri200Response } from '../../../api-client/model/getMatrixBotOAuth2RedirectUri200Response';
import { HandleMatrixBotOAuth2Callback200Response } from '../../../api-client/model/handleMatrixBotOAuth2Callback200Response';
import { MatrixBotStatus } from '../../../api-client/model/matrixBotStatus';
import { PollMatrixBotDeviceCode200Response } from '../../../api-client/model/pollMatrixBotDeviceCode200Response';

export interface AdminMatrixBotService {
    getStatus(): Observable<MatrixBotStatus>;
    getOAuth2RedirectUri(): Observable<GetMatrixBotOAuth2RedirectUri200Response>;
    handleOAuth2Callback(code: string, state: string): Observable<HandleMatrixBotOAuth2Callback200Response>;
    getDeviceCode(): Observable<GetMatrixBotDeviceCode200Response>;
    pollDeviceCode(deviceCode: string): Observable<PollMatrixBotDeviceCode200Response>;
    initializeBot(): Observable<{ success: boolean; message: string }>;
}


