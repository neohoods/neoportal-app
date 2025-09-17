import { CommonModule, NgIf } from '@angular/common';
import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { TuiHint } from '@taiga-ui/core';
import { UIApplication } from '../../../../models/UIApplication';

@Component({
  selector: 'application',
  imports: [TuiHint, CommonModule, NgIf, TranslateModule],
  templateUrl: './application.component.html',
  styleUrl: './application.component.scss'
})
export class ApplicationComponent {
  @Input({ required: true }) application!: UIApplication;

  openLink(url: string): void {
    window.open(url, '_blank'); // opens in a new tab like target="_blank"
  }
}
