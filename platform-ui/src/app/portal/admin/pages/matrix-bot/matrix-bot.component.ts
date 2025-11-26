import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiIcon, TuiLabel, TuiLoader, TuiTextfield } from '@taiga-ui/core';
import { GetMatrixBotDeviceCode200Response } from '../../../../api-client/model/getMatrixBotDeviceCode200Response';
import { MatrixBotStatus, MatrixBotStatusMatrixAccessEnum } from '../../../../api-client/model/matrixBotStatus';
import { ADMIN_MATRIX_BOT_SERVICE_TOKEN } from '../../admin.providers';

@Component({
    selector: 'app-matrix-bot',
    standalone: true,
    imports: [
        CommonModule,
        TuiButton,
        TuiIcon,
        TuiLabel,
        TuiLoader,
        TuiTextfield,
        TranslateModule
    ],
    templateUrl: './matrix-bot.component.html',
    styleUrls: ['./matrix-bot.component.scss']
})
export class MatrixBotComponent implements OnInit {
    private matrixBotService = inject(ADMIN_MATRIX_BOT_SERVICE_TOKEN);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private alertService = inject(TuiAlertService);
    private translate = inject(TranslateService);

    status = signal<MatrixBotStatus | null>(null);
    loading = signal(false);
    connecting = signal(false);
    initializing = signal(false);

    // Device code flow state
    deviceCodeInfo = signal<GetMatrixBotDeviceCode200Response | null>(null);
    deviceCodeStatus = signal<'idle' | 'pending' | 'success' | 'error'>('idle');
    deviceCodeMessage = signal<string>('');

    ngOnInit() {
        // Check for OAuth2 callback
        this.route.queryParams.subscribe(params => {
            if (params['code'] && params['state']) {
                this.handleOAuth2Callback(params['code'], params['state']);
            } else {
                this.loadStatus();
            }
        });
    }

    loadStatus() {
        this.loading.set(true);
        this.matrixBotService.getStatus()
            .subscribe({
                next: (status) => {
                    this.status.set(status);
                    this.loading.set(false);
                },
                error: (error) => {
                    console.error('Failed to load Matrix bot status', error);
                    this.loading.set(false);
                    this.translate.get('matrixBot.error.loadStatus').subscribe((message: string) => {
                        this.alertService.open(message, { appearance: 'negative' }).subscribe();
                    });
                }
            });
    }

    connectBot() {
        this.connecting.set(true);
        this.matrixBotService.getOAuth2RedirectUri()
            .subscribe({
                next: (response) => {
                    if (response.redirectUri) {
                        window.location.href = response.redirectUri;
                    } else {
                        this.connecting.set(false);
                        this.translate.get('matrixBot.error.noRedirectUri').subscribe((message: string) => {
                            this.alertService.open(message, { appearance: 'negative' }).subscribe();
                        });
                    }
                },
                error: (error) => {
                    console.error('Failed to get OAuth2 redirect URI', error);
                    this.connecting.set(false);
                    this.translate.get('matrixBot.error.getRedirectUri').subscribe((message: string) => {
                        this.alertService.open(message, { appearance: 'negative' }).subscribe();
                    });
                }
            });
    }

    handleOAuth2Callback(code: string, state: string) {
        this.connecting.set(true);
        this.matrixBotService.handleOAuth2Callback(code, state)
            .subscribe({
                next: (response) => {
                    this.connecting.set(false);
                    if (response.success) {
                        this.translate.get('matrixBot.success.connected').subscribe((message: string) => {
                            this.alertService.open(message, { appearance: 'positive' }).subscribe();
                        });
                        // Remove query params and reload status
                        this.router.navigate([], { relativeTo: this.route, queryParams: {} });
                        this.loadStatus();
                    } else {
                        this.translate.get('matrixBot.error.connectionFailed').subscribe((message: string) => {
                            this.alertService.open(message, { appearance: 'negative' }).subscribe();
                        });
                    }
                },
                error: (error) => {
                    console.error('Failed to handle OAuth2 callback', error);
                    this.connecting.set(false);
                    this.translate.get('matrixBot.error.callbackFailed').subscribe((message: string) => {
                        this.alertService.open(message, { appearance: 'negative' }).subscribe();
                    });
                }
            });
    }

    getStatusIcon(status: MatrixBotStatusMatrixAccessEnum): string {
        return status === MatrixBotStatusMatrixAccessEnum.Ok ? '✓' : '✗';
    }

    getStatusColor(status: MatrixBotStatusMatrixAccessEnum): string {
        return status === MatrixBotStatusMatrixAccessEnum.Ok ? 'green' : 'red';
    }


    connectBotWithCurrentUser() {
        this.connectBot();
    }

    connectBotWithDeviceCode() {
        this.connecting.set(true);
        this.deviceCodeStatus.set('idle');
        this.deviceCodeInfo.set(null);

        this.matrixBotService.getDeviceCode()
            .subscribe({
                next: (response) => {
                    this.deviceCodeInfo.set(response);
                    this.deviceCodeStatus.set('pending');
                    this.deviceCodeMessage.set(this.translate.instant('matrixBot.deviceCode.status.pending'));
                    this.connecting.set(false);
                    // Don't poll immediately - wait for user to authorize and click verify button
                },
                error: (error) => {
                    console.error('Failed to get device code', error);
                    this.connecting.set(false);
                    this.deviceCodeStatus.set('error');
                    this.translate.get('matrixBot.error.getDeviceCode').subscribe((message: string) => {
                        this.deviceCodeMessage.set(message);
                        this.alertService.open(message, { appearance: 'negative' }).subscribe();
                    });
                }
            });
    }

    verifyDeviceCode() {
        const deviceCodeInfo = this.deviceCodeInfo();
        if (!deviceCodeInfo || !deviceCodeInfo.deviceCode) {
            return;
        }

        this.connecting.set(true);
        this.deviceCodeStatus.set('pending');
        this.deviceCodeMessage.set(this.translate.instant('matrixBot.deviceCode.status.pending'));

        // Backend handles polling internally, just call once and wait
        this.matrixBotService.pollDeviceCode(deviceCodeInfo.deviceCode)
            .subscribe({
                next: (pollResponse) => {
                    this.connecting.set(false);
                    if (pollResponse.status === 'success') {
                        this.deviceCodeStatus.set('success');
                        this.deviceCodeMessage.set(pollResponse.message || this.translate.instant('matrixBot.deviceCode.status.success'));
                        this.translate.get('matrixBot.deviceCode.status.success').subscribe((message: string) => {
                            this.alertService.open(message, { appearance: 'positive' }).subscribe();
                        });
                        this.loadStatus();
                    } else if (pollResponse.status === 'error') {
                        this.deviceCodeStatus.set('error');
                        this.deviceCodeMessage.set(pollResponse.message || this.translate.instant('matrixBot.deviceCode.status.error'));
                        this.translate.get('matrixBot.deviceCode.status.error').subscribe((message: string) => {
                            this.alertService.open(message, { appearance: 'negative' }).subscribe();
                        });
                    }
                },
                error: (error) => {
                    console.error('Failed to poll device code', error);
                    this.connecting.set(false);
                    this.deviceCodeStatus.set('error');
                    this.translate.get('matrixBot.error.pollDeviceCode').subscribe((message: string) => {
                        this.deviceCodeMessage.set(message);
                        this.alertService.open(message, { appearance: 'negative' }).subscribe();
                    });
                }
            });
    }

    copyDeviceCodeLink() {
        const info = this.deviceCodeInfo();
        const link = info?.verificationUriComplete;
        if (link) {
            navigator.clipboard.writeText(link).then(() => {
                this.translate.get('matrixBot.deviceCode.linkCopied').subscribe((message: string) => {
                    this.alertService.open(message, { appearance: 'positive' }).subscribe();
                });
            }).catch(err => {
                console.error('Failed to copy link', err);
                this.translate.get('matrixBot.deviceCode.copyFailed').subscribe((message: string) => {
                    this.alertService.open(message, { appearance: 'negative' }).subscribe();
                });
            });
        }
    }

    copyUserCode() {
        const info = this.deviceCodeInfo();
        const userCode = info?.userCode;
        if (userCode) {
            navigator.clipboard.writeText(userCode).then(() => {
                this.translate.get('matrixBot.deviceCode.codeCopied').subscribe((message: string) => {
                    this.alertService.open(message, { appearance: 'positive' }).subscribe();
                });
            }).catch(err => {
                console.error('Failed to copy user code', err);
                this.translate.get('matrixBot.deviceCode.copyFailed').subscribe((message: string) => {
                    this.alertService.open(message, { appearance: 'negative' }).subscribe();
                });
            });
        }
    }

    initializeBot() {
        this.initializing.set(true);
        this.matrixBotService.initializeBot()
            .subscribe({
                next: (response) => {
                    this.initializing.set(false);
                    if (response.success) {
                        this.translate.get('matrixBot.success.initialized').subscribe((message: string) => {
                            this.alertService.open(message, { appearance: 'positive' }).subscribe();
                        });
                        // Reload status after a short delay to allow initialization to start
                        setTimeout(() => {
                            this.loadStatus();
                        }, 2000);
                    } else {
                        this.translate.get('matrixBot.error.initialize').subscribe((message: string) => {
                            this.alertService.open(message + ': ' + response.message, { appearance: 'negative' }).subscribe();
                        });
                    }
                },
                error: (error) => {
                    console.error('Failed to initialize Matrix bot', error);
                    this.initializing.set(false);
                    this.translate.get('matrixBot.error.initialize').subscribe((message: string) => {
                        this.alertService.open(message, { appearance: 'negative' }).subscribe();
                    });
                }
            });
    }
}

