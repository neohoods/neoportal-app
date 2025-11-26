import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { GetMatrixBotDeviceCode200Response } from '../../../../api-client/model/getMatrixBotDeviceCode200Response';
import { GetMatrixBotOAuth2RedirectUri200Response } from '../../../../api-client/model/getMatrixBotOAuth2RedirectUri200Response';
import { HandleMatrixBotOAuth2Callback200Response } from '../../../../api-client/model/handleMatrixBotOAuth2Callback200Response';
import { MatrixBotStatus, MatrixBotStatusMatrixAccessEnum } from '../../../../api-client/model/matrixBotStatus';
import { PollMatrixBotDeviceCode200Response, PollMatrixBotDeviceCode200ResponseStatusEnum } from '../../../../api-client/model/pollMatrixBotDeviceCode200Response';
import { AdminMatrixBotService } from '../matrix-bot.service';

@Injectable({
    providedIn: 'root',
})
export class MockAdminMatrixBotService implements AdminMatrixBotService {
    private mockStatus: MatrixBotStatus = {
        enabled: true,
        disabled: false,
        matrixAccess: MatrixBotStatusMatrixAccessEnum.Ok,
        hasRefreshToken: false,
        hasAccessToken: false,
        spaceId: undefined,
        currentSpaces: [
            { spaceId: '!space1:chat.neohoods.com', name: 'Terres de Laya' },
            { spaceId: '!space2:chat.neohoods.com', name: 'Neohoods Dev' }
        ],
        error: undefined
    };

    getStatus(): Observable<MatrixBotStatus> {
        return of(this.mockStatus).pipe(delay(300));
    }

    getOAuth2RedirectUri(): Observable<GetMatrixBotOAuth2RedirectUri200Response> {
        return of({
            redirectUri: 'https://mas.chat.neohoods.com/oauth2/authorize?client_id=01KAKRXM4EXRFD2W9HQEYFV7CA&redirect_uri=https://local.portal.neohoods.com:4200/api/admin/matrix-bot/oauth2/callback&response_type=code&scope=openid&state=mock-state'
        }).pipe(delay(200));
    }

    handleOAuth2Callback(code: string, state: string): Observable<HandleMatrixBotOAuth2Callback200Response> {
        // Simulate successful callback
        this.mockStatus.hasRefreshToken = true;
        this.mockStatus.hasAccessToken = true;
        this.mockStatus.matrixAccess = MatrixBotStatusMatrixAccessEnum.Ok;

        return of({
            success: true,
            message: 'OAuth2 tokens saved successfully'
        }).pipe(delay(500));
    }

    getDeviceCode(): Observable<GetMatrixBotDeviceCode200Response> {
        return of({
            deviceCode: 'mock-device-code-12345',
            userCode: 'ABCD-EFGH',
            verificationUri: 'https://mas.chat.neohoods.com/verify',
            verificationUriComplete: 'https://mas.chat.neohoods.com/verify?user_code=ABCD-EFGH',
            expiresIn: 900,
            interval: 5
        }).pipe(delay(200));
    }

    pollDeviceCode(deviceCode: string): Observable<PollMatrixBotDeviceCode200Response> {
        // Simulate pending for first few calls, then success
        const isSuccess = Math.random() > 0.5;
        return of({
            status: isSuccess ? PollMatrixBotDeviceCode200ResponseStatusEnum.Success : PollMatrixBotDeviceCode200ResponseStatusEnum.Pending,
            message: isSuccess ? 'OAuth2 tokens saved successfully' : 'Authorization still pending',
            accessToken: isSuccess ? 'mock-access-token' : undefined
        }).pipe(delay(500));
    }

    initializeBot(): Observable<{ success: boolean; message: string }> {
        // Simulate initialization with delay
        return of({
            success: true,
            message: 'Matrix bot initialization started successfully'
        }).pipe(delay(1000));
    }
}


