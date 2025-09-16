import { UINewsletter } from '../models/UINewsletter';

export class NewslettersLoader {
    static async loadNewsletters(): Promise<UINewsletter[]> {
        const response = await fetch('/app/mock/newsletters.json');
        return await response.json();
    }
}
