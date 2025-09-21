import { Injectable } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { environment } from '../../environments/environment';

@Injectable({
    providedIn: 'root'
})
export class TitleService {
    constructor(private title: Title) { }

    setTitle(pageTitle?: string): void {
        const baseTitle = environment.brandDisplayName;
        if (pageTitle) {
            this.title.setTitle(`${pageTitle} - ${baseTitle}`);
        } else {
            this.title.setTitle(baseTitle);
        }
    }
}
