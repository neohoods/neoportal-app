import { Injectable } from '@angular/core';

@Injectable({
    providedIn: 'root'
})
export class CookieService {
    private readonly COOKIE_PREFIX = 'neohoods_';
    private readonly DEFAULT_EXPIRY_DAYS = 365; // 1 an

    /**
     * Définit un cookie avec une valeur et une durée d'expiration
     * @param name Nom du cookie
     * @param value Valeur du cookie
     * @param days Nombre de jours avant expiration (défaut: 1 an)
     */
    setCookie(name: string, value: string, days: number = this.DEFAULT_EXPIRY_DAYS): void {
        const expires = new Date();
        expires.setTime(expires.getTime() + (days * 24 * 60 * 60 * 1000));
        const cookieValue = `${this.COOKIE_PREFIX}${name}=${value};expires=${expires.toUTCString()};path=/;SameSite=Lax`;
        document.cookie = cookieValue;
    }

    /**
     * Récupère la valeur d'un cookie
     * @param name Nom du cookie
     * @returns La valeur du cookie ou null si non trouvé
     */
    getCookie(name: string): string | null {
        const cookieName = `${this.COOKIE_PREFIX}${name}=`;
        const cookies = document.cookie.split(';');

        for (let cookie of cookies) {
            cookie = cookie.trim();
            if (cookie.indexOf(cookieName) === 0) {
                return cookie.substring(cookieName.length);
            }
        }
        return null;
    }

    /**
     * Supprime un cookie
     * @param name Nom du cookie
     */
    deleteCookie(name: string): void {
        document.cookie = `${this.COOKIE_PREFIX}${name}=;expires=Thu, 01 Jan 1970 00:00:00 UTC;path=/;`;
    }

    /**
     * Vérifie si un cookie existe
     * @param name Nom du cookie
     * @returns true si le cookie existe, false sinon
     */
    hasCookie(name: string): boolean {
        return this.getCookie(name) !== null;
    }

    /**
     * Méthodes spécifiques pour les notifications
     */

    /**
     * Mémorise que l'utilisateur a fermé la notification des annonces
     */
    setAnnouncementsNotificationClosed(): void {
        this.setCookie('announcements_notification_closed', 'true');
    }

    /**
     * Vérifie si l'utilisateur a fermé la notification des annonces
     * @returns true si la notification a été fermée, false sinon
     */
    isAnnouncementsNotificationClosed(): boolean {
        return this.getCookie('announcements_notification_closed') === 'true';
    }

    /**
     * Mémorise que l'utilisateur a fermé la notification des applications
     */
    setApplicationsNotificationClosed(): void {
        this.setCookie('applications_notification_closed', 'true');
    }

    /**
     * Vérifie si l'utilisateur a fermé la notification des applications
     * @returns true si la notification a été fermée, false sinon
     */
    isApplicationsNotificationClosed(): boolean {
        return this.getCookie('applications_notification_closed') === 'true';
    }
}
