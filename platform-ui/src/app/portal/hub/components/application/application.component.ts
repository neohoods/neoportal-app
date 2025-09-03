import { Component, Input } from '@angular/core';
import { TuiHint } from '@taiga-ui/core';
import { UIApplication } from '../../../../models/UIApplication';

@Component({
  selector: 'application',
  imports: [TuiHint],
  templateUrl: './application.component.html',
  styleUrl: './application.component.scss'
})
export class ApplicationComponent {
  @Input({ required: true }) application!: UIApplication;

  openLink(url: string): void {
    window.open(url, '_blank'); // opens in a new tab like target="_blank"
  }
}
