import { NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TuiAlertService, TuiButton } from '@taiga-ui/core';
import { environment } from '../../../../../../environments/environment';

@Component({
  standalone: true,
  selector: 'app-email-confirmation',
  imports: [
    TuiButton,
    TranslateModule,
    NgIf
  ],
  templateUrl: './email-confirmation.component.html',
  styleUrl: './email-confirmation.component.scss'
})
export class EmailConfirmationComponent implements OnInit {
  isVerified = false;
  source = '';
  environment = environment;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private alerts: TuiAlertService
  ) { }

  ngOnInit() {
    // Check URL parameters
    this.route.queryParams.subscribe(params => {
      this.isVerified = params['verified'] === 'true';
      this.source = params['source'] || '';

      if (this.isVerified) {
        // Show success message and redirect to login
        this.alerts.open(
          'Email vérifié avec succès ! Vous pouvez maintenant vous connecter.',
          { appearance: 'positive' }
        ).subscribe(() => {
          // Redirect to login after showing the message
          this.router.navigate(['/login'], {
            queryParams: {
              verified: 'true',
              message: 'email-verified'
            }
          });
        });
      } else {
        this.error = 'Erreur lors de la vérification de l\'email';
      }
    });
  }

  goToLogin() {
    this.router.navigate(['/login']);
  }

  goToHome() {
    this.router.navigate(['/']);
  }
}