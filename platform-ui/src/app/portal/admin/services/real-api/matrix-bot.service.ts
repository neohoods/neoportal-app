import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MatrixBotAdminApiService } from '../../../../api-client/api/matrixBotAdminApi.service';
import { GetMatrixBotDeviceCode200Response } from '../../../../api-client/model/getMatrixBotDeviceCode200Response';
import { GetMatrixBotOAuth2RedirectUri200Response } from '../../../../api-client/model/getMatrixBotOAuth2RedirectUri200Response';
import { HandleMatrixBotOAuth2Callback200Response } from '../../../../api-client/model/handleMatrixBotOAuth2Callback200Response';
import { InitializeMatrixBot200Response } from '../../../../api-client/model/initializeMatrixBot200Response';
import { MatrixBotStatus } from '../../../../api-client/model/matrixBotStatus';
import { PollMatrixBotDeviceCode200Response } from '../../../../api-client/model/pollMatrixBotDeviceCode200Response';
import { PollMatrixBotDeviceCodeRequest } from '../../../../api-client/model/pollMatrixBotDeviceCodeRequest';
import { AdminMatrixBotService } from '../matrix-bot.service';

@Injectable({
    providedIn: 'root',
})
export class RealApiAdminMatrixBotService implements AdminMatrixBotService {
    constructor(private matrixBotAdminApi: MatrixBotAdminApiService) { }

    getStatus(): Observable<MatrixBotStatus> {
        return this.matrixBotAdminApi.getMatrixBotStatus();
    }

    getOAuth2RedirectUri(): Observable<GetMatrixBotOAuth2RedirectUri200Response> {
        return this.matrixBotAdminApi.getMatrixBotOAuth2RedirectUri();
    }

    handleOAuth2Callback(code: string, state: string): Observable<HandleMatrixBotOAuth2Callback200Response> {
        return this.matrixBotAdminApi.handleMatrixBotOAuth2Callback(code, state);
    }

    getDeviceCode(): Observable<GetMatrixBotDeviceCode200Response> {
        return this.matrixBotAdminApi.getMatrixBotDeviceCode();
    }

    pollDeviceCode(deviceCode: string): Observable<PollMatrixBotDeviceCode200Response> {
        const requestBody: PollMatrixBotDeviceCodeRequest = { deviceCode: deviceCode };
        return this.matrixBotAdminApi.pollMatrixBotDeviceCode(requestBody);
    }

    initializeBot(): Observable<{ success: boolean; message: string }> {
        return this.matrixBotAdminApi.initializeMatrixBot().pipe(
            map((response: InitializeMatrixBot200Response) => ({
                success: response.success ?? false,
                message: response.message ?? ''
            }))
        );
    }
}

