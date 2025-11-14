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
import { CATEGORY_COLORS, CATEGORY_ICONS, UIAnnouncementCategory } from '../../models/UIAnnoncements';
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

  /**
   * Converts API category string (e.g., "COMMUNITY_EVENT") to UIAnnouncementCategory enum
   */
  private convertCategoryToEnum(categoryString: string): UIAnnouncementCategory {
    // Convert from UPPER_SNAKE_CASE to PascalCase
    const parts = categoryString.toLowerCase().split('_');
    const pascalCase = parts.map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('');
    return pascalCase as UIAnnouncementCategory;
  }

  getNotificationIcon(notification: UINotification): string {
    switch (notification.type) {
      case UINotificationType.ADMIN_NEW_USER:
        return '@tui.user';
      case UINotificationType.NEW_ANNOUNCEMENT:
        // Get icon based on announcement category
        if (notification.payload?.announcementCategory) {
          try {
            const category = this.convertCategoryToEnum(notification.payload.announcementCategory);
            return CATEGORY_ICONS[category] || CATEGORY_ICONS[UIAnnouncementCategory.Other];
          } catch {
            return CATEGORY_ICONS[UIAnnouncementCategory.Other];
          }
        }
        return CATEGORY_ICONS[UIAnnouncementCategory.Other];
      case UINotificationType.RESERVATION:
        return '@tui.calendar';
      case UINotificationType.UNIT_INVITATION:
        return '@tui.user-plus';
      case UINotificationType.UNIT_JOIN_REQUEST:
        return '@tui.user-check';
      default:
        return '@tui.bell';
    }
  }

  getNotificationIconColor(notification: UINotification): string | null {
    switch (notification.type) {
      case UINotificationType.NEW_ANNOUNCEMENT:
        // Get color based on announcement category
        if (notification.payload?.announcementCategory) {
          try {
            const category = this.convertCategoryToEnum(notification.payload.announcementCategory);
            return CATEGORY_COLORS[category] || CATEGORY_COLORS[UIAnnouncementCategory.Other];
          } catch {
            return CATEGORY_COLORS[UIAnnouncementCategory.Other];
          }
        }
        return CATEGORY_COLORS[UIAnnouncementCategory.Other];
      default:
        return null; // Use default color
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

}
