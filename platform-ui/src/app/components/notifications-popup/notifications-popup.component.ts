import { animate, style, transition, trigger } from '@angular/animations';
import {
  Component,
  effect,
  ElementRef,
  HostListener,
  Inject,
  OnDestroy,
  OnInit,
  output,
  signal
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TuiButton, TuiIcon } from '@taiga-ui/core';
import { TuiBadgeNotification } from '@taiga-ui/kit';
import { interval, Subscription } from 'rxjs';
import { takeWhile } from 'rxjs/operators';
import {
  getGlobalProviders,
  NOTIFICATIONS_SERVICE_TOKEN,
} from '../../global.provider';
import { UINotification, UINotificationType } from '../../models/UINotification';
import { hubProviders } from '../../portal/hub/hub.provider';
import {
  NotificationsService
} from '../../services/notifications.service';
import { SharedModule } from '../shared-module/shared-module.component';

@Component({
  standalone: true,
  selector: 'notifications-popup',
  imports: [TranslateModule, TuiBadgeNotification, TuiButton, TuiIcon, SharedModule],
  providers: [...getGlobalProviders(), ...hubProviders],
  templateUrl: './notifications-popup.component.html',
  styleUrls: ['./notifications-popup.component.scss'],
  animations: [
    trigger('pulseAnimation', [
      transition('* => true', [
        style({ transform: 'scale(1)' }),
        animate('300ms ease-in-out', style({ transform: 'scale(1.2)' })),
        animate('300ms ease-in-out', style({ transform: 'scale(1)' }))
      ])
    ])
  ]
})
export class NotificationsPopupComponent implements OnInit, OnDestroy {
  private readonly POLLING_INTERVAL = 30000; // Poll every 30 seconds
  private pollingSubscription?: Subscription;
  private isAlive = true; // Track component lifecycle

  // Expose enum for template
  UINotificationType = UINotificationType;

  // Use signals for reactive state
  notifications = signal<UINotification[]>([]);
  unreadNotificationsCount = signal<number>(0);
  hasNewNotifications = signal(false);
  isPopupVisible = signal(false);
  isPopupVisibleChange = output<boolean>();
  refreshNotificationsSignal = signal(false);

  constructor(
    private elRef: ElementRef,
    private router: Router,
    @Inject(NOTIFICATIONS_SERVICE_TOKEN)
    private notificationsService: NotificationsService,
  ) {
    // Effect to handle notifications refresh
    effect(() => {
      if (this.refreshNotificationsSignal()) {
        this.refreshNotificationsSignal.set(false);
        this.refreshNotifications();
      }
    });
  }

  ngOnInit() {
    this.refreshNotifications();
    this.startPolling();
  }

  ngOnDestroy() {
    this.isAlive = false;
    if (this.pollingSubscription) {
      this.pollingSubscription.unsubscribe();
    }
  }

  private startPolling() {
    // Start polling for notifications
    this.pollingSubscription = interval(this.POLLING_INTERVAL)
      .pipe(
        takeWhile(() => this.isAlive)
      )
      .subscribe(() => {
        this.refreshNotifications();
      });
  }

  refreshNotifications() {
    this.notificationsService
      .getNotifications()
      .subscribe({
        next: (notifications: UINotification[]) => {
          const previousCount = this.unreadNotificationsCount();

          // Update notifications signal
          this.notifications.set(notifications);

          // Calculate unread count from the notifications
          const newUnreadCount = notifications.filter(n => !n.alreadyRead).length;
          this.unreadNotificationsCount.set(newUnreadCount);

          // Trigger animation if we have new notifications
          if (newUnreadCount > previousCount) {
            this.hasNewNotifications.set(true);
            setTimeout(() => {
              this.hasNewNotifications.set(false);
            }, 600);
          }
        },
        error: (error) => {
          console.error('Failed to fetch notifications:', error);
        }
      });
  }

  toggleNotificationsPopup() {
    const newVisibility = !this.isPopupVisible();
    this.isPopupVisible.set(newVisibility);

    // If opening the popup, refresh notifications
    if (newVisibility) {
      this.refreshNotifications();
    }
  }

  markAsRead(notification: UINotification): void {
    // Update the notification's read status
    const updatedNotifications = this.notifications().map(n =>
      n.id === notification.id ? { ...n, alreadyRead: true } : n
    );

    // Update notifications signal
    this.notifications.set(updatedNotifications);

    // Update unread count
    this.unreadNotificationsCount.set(updatedNotifications.filter(n => !n.alreadyRead).length);

    // Notify the service
    this.notificationsService.acknowledgeNotifications([notification]);
  }

  markAllAsRead(): void {
    // Update all notifications as read
    const updatedNotifications = this.notifications().map(n => ({ ...n, alreadyRead: true }));

    // Update notifications signal
    this.notifications.set(updatedNotifications);

    // Reset unread count
    this.unreadNotificationsCount.set(0);

    // Notify the service
    this.notificationsService.acknowledgeNotifications(this.notifications());
  }

  getNotificationText(notification: UINotification): string {
    switch (notification.type) {
      case UINotificationType.ADMIN_NEW_USER:
        if (notification.payload) {
          return 'notifications.adminNewUserWithName';
        }
        return 'notifications.adminNewUser';
      case UINotificationType.NEW_ANNOUNCEMENT:
        if (notification.payload?.announcementTitle) {
          return 'notifications.newAnnouncementWithTitle';
        }
        return 'notifications.newAnnouncement';
      case UINotificationType.RESERVATION:
        return 'notifications.reservation';
      case UINotificationType.UNIT_INVITATION:
        if (notification.payload?.invitedBy) {
          return 'notifications.unitInvitationWithInviter';
        }
        return 'notifications.unitInvitation';
      case UINotificationType.UNIT_JOIN_REQUEST:
        return 'notifications.unitJoinRequest';
      default:
        return notification.translationKey || 'notifications.unknown';
    }
  }

  getNotificationVariables(notification: UINotification): any {
    switch (notification.type) {
      case UINotificationType.ADMIN_NEW_USER:
        if (notification.payload) {
          return {
            firstName: notification.payload.newUserFirstName,
            lastName: notification.payload.newUserLastName,
            email: notification.payload.newUserEmail
          };
        }
        return {};
      case UINotificationType.NEW_ANNOUNCEMENT:
        if (notification.payload) {
          return {
            title: notification.payload.announcementTitle || notification.payload.title
          };
        }
        return {};
      case UINotificationType.UNIT_INVITATION:
        if (notification.payload) {
          return {
            invitedBy: notification.payload.invitedBy
          };
        }
        return {};
      default:
        return {};
    }
  }

  getNotificationLink(notification: UINotification): string | null {
    switch (notification.type) {
      case UINotificationType.ADMIN_NEW_USER:
        // Navigate to admin users page with the specific user
        if (notification.payload?.newUserId) {
          return `/admin/users/${notification.payload.newUserId}/edit`;
        }
        return '/admin/users';

      case UINotificationType.NEW_ANNOUNCEMENT:
        // Navigate to hub/wall with announcementId query parameter
        if (notification.payload?.announcementId) {
          return `/hub/wall?announcementId=${notification.payload.announcementId}`;
        }
        return '/hub/wall';

      // Add other cases if needed for different notification types
      default:
        return null;
    }
  }

  getNotificationImage(notification: UINotification): string {
    switch (notification.type) {
      case UINotificationType.ADMIN_NEW_USER:
        return '/assets/notifications/user-icon.svg';
      // Add more cases as needed for other notification types
      // case UINotificationType.ANNOUNCEMENT:
      //   return '/assets/notifications/announcement-icon.svg';
      // case UINotificationType.EMAIL:
      //   return '/assets/notifications/email-icon.svg';
      default:
        return '/assets/notifications/default-icon.svg';
    }
  }

  onNotificationClick(notification: UINotification) {
    // Handle notification click depending on its type or other properties
    const link = this.getNotificationLink(notification);
    if (link) {
      // Check if we're already on the target route
      const currentUrl = this.router.url;
      const targetUrl = link.split('?')[0];

      if (currentUrl.startsWith(targetUrl)) {
        // Already on the target route, just update query params
        const urlParts = link.split('?');
        if (urlParts.length > 1) {
          const queryParams = new URLSearchParams(urlParts[1]);
          const params: any = {};
          queryParams.forEach((value, key) => {
            params[key] = value;
          });

          this.router.navigate([targetUrl], { queryParams: params, replaceUrl: false }).then(() => {
            this.closePopup();
          }).catch((error) => {
            console.error('Navigation error:', error);
          });
        } else {
          this.closePopup();
        }
      } else {
        // Navigate to new route
        this.router.navigateByUrl(link).then(() => {
          this.closePopup();
        }).catch((error) => {
          console.error('Navigation error:', error);
        });
      }
    }
  }

  openPopup() {
    this.refreshNotifications();
    this.isPopupVisible.set(true);
  }

  // Close the popup (manually triggered or on outside click)
  closePopup() {
    this.isPopupVisible.set(false);
  }

  // Close the popup when clicking outside of it
  @HostListener('document:click', ['$event'])
  onClickOutside(event: MouseEvent) {
    if (!this.elRef.nativeElement.contains(event.target)) {
      this.closePopup();
    }
  }

  onNotificationHover(notification: UINotification) {
    this.markAsRead(notification);
  }

  onImageError(event: Event) {
    const img = event.target as HTMLImageElement;
    if (img) {
      img.src = '/assets/notifications/default-icon.svg';
    }
  }
}
