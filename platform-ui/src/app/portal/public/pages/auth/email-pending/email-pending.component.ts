import { NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TuiButton } from '@taiga-ui/core';
import { environment } from '../../../../../../environments/environment';
import { WelcomeComponent } from '../../../../../components/welcome/welcome.component';
import { getGlobalProviders } from '../../../../../global.provider';

@Component({
    standalone: true,
    selector: 'app-email-pending',
    imports: [
        WelcomeComponent,
        TuiButton,
        TranslateModule,
        NgIf
    ],
    templateUrl: './email-pending.component.html',
    styleUrl: './email-pending.component.scss',
    providers: [...getGlobalProviders()],
})
export class EmailPendingComponent implements OnInit {
    email: string | null = null;
    environment = environment;

    constructor(private router: Router) { }

    ngOnInit(): void {
        // Get email from query parameters if available
        const urlParams = new URLSearchParams(window.location.search);
        this.email = urlParams.get('email');
    }

    goToSignIn(): void {
        this.router.navigate(['/login']);
    }

    goToHome(): void {
        this.router.navigate(['/']);
    }

}
