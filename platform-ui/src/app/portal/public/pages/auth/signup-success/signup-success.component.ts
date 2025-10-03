import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TuiButton, TuiIcon } from '@taiga-ui/core';
import { environment } from '../../../../../../environments/environment';
import { WelcomeComponent } from '../../../../../components/welcome/welcome.component';
import { getGlobalProviders } from '../../../../../global.provider';

@Component({
    standalone: true,
    selector: 'app-signup-success',
    imports: [
        CommonModule,
        WelcomeComponent,
        TuiButton,
        TuiIcon,
        TranslateModule
    ],
    templateUrl: './signup-success.component.html',
    styleUrl: './signup-success.component.scss',
    providers: [...getGlobalProviders()],
})
export class SignupSuccessComponent implements OnInit {
    email: string | null = null;
    environment = environment;

    constructor(private router: Router) { }

    ngOnInit(): void {
        // Get email from query params or state
        const urlParams = new URLSearchParams(window.location.search);
        this.email = urlParams.get('email');
    }

    goToHub(): void {
        this.router.navigate(['/hub']);
    }

    goToHome(): void {
        this.router.navigate(['/']);
    }
}
