import { TestBed } from '@angular/core/testing';
import { CookieService } from './cookie.service';

describe('CookieService', () => {
    let service: CookieService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(CookieService);

        // Clear all cookies before each test
        document.cookie.split(";").forEach(function (c) {
            document.cookie = c.replace(/^ +/, "").replace(/=.*/, "=;expires=" + new Date().toUTCString() + ";path=/");
        });
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should set and get a cookie', () => {
        service.setCookie('test', 'value');
        expect(service.getCookie('test')).toBe('value');
    });

    it('should check if a cookie exists', () => {
        expect(service.hasCookie('test')).toBeFalsy();
        service.setCookie('test', 'value');
        expect(service.hasCookie('test')).toBeTruthy();
    });

    it('should delete a cookie', () => {
        service.setCookie('test', 'value');
        expect(service.getCookie('test')).toBe('value');
        service.deleteCookie('test');
        expect(service.getCookie('test')).toBeNull();
    });

    it('should handle announcements notification state', () => {
        expect(service.isAnnouncementsNotificationClosed()).toBeFalsy();
        service.setAnnouncementsNotificationClosed();
        expect(service.isAnnouncementsNotificationClosed()).toBeTruthy();
    });

    it('should handle applications notification state', () => {
        expect(service.isApplicationsNotificationClosed()).toBeFalsy();
        service.setApplicationsNotificationClosed();
        expect(service.isApplicationsNotificationClosed()).toBeTruthy();
    });
});

