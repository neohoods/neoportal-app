import { CommonModule, NgIf } from '@angular/common';
import { Component, inject, Input } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TuiHint, TuiIcon } from '@taiga-ui/core';
import { UIApplication } from '../../../../models/UIApplication';

@Component({
  selector: 'application',
  imports: [TuiHint, TuiIcon, CommonModule, NgIf, TranslateModule],
  templateUrl: './application.component.html',
  styleUrl: './application.component.scss'
})
export class ApplicationComponent {
  @Input({ required: true }) application!: UIApplication;
  private router = inject(Router);

  isTuiIcon(icon: string): boolean {
    return icon.startsWith('@tui.');
  }

  openLink(url: string): void {
    // Check if URL is external (starts with http:// or https://)
    if (url.startsWith('http://') || url.startsWith('https://')) {
      window.open(url, '_blank'); // opens in a new tab for external links
    } else {
      // Internal link - navigate using Angular router
      this.router.navigateByUrl(url);
    }
  }
}
