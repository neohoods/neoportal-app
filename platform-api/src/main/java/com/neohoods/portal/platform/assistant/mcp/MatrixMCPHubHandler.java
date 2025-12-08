package com.neohoods.portal.platform.assistant.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.neohoods.portal.platform.entities.UserEntity;
import com.neohoods.portal.platform.repositories.UsersRepository;
import com.neohoods.portal.platform.services.AnnouncementsService;
import com.neohoods.portal.platform.services.ApplicationsService;
import com.neohoods.portal.platform.services.InfosService;
import com.neohoods.portal.platform.services.NotificationsService;

import com.neohoods.portal.platform.assistant.services.MatrixAssistantAdminCommandService;
import com.neohoods.portal.platform.assistant.model.MatrixAssistantAuthContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler for hub-related MCP tools (public endpoints)
 */
@Component
@Slf4j
public class MatrixMCPHubHandler extends MatrixMCPBaseHandler {

        private final InfosService infosService;
        private final AnnouncementsService announcementsService;
        private final ApplicationsService applicationsService;
        private final NotificationsService notificationsService;

        public MatrixMCPHubHandler(
                        MessageSource messageSource,
                        UsersRepository usersRepository,
                        @Autowired(required = false) MatrixAssistantAdminCommandService adminCommandService,
                        InfosService infosService,
                        AnnouncementsService announcementsService,
                        ApplicationsService applicationsService,
                        NotificationsService notificationsService) {
                super(messageSource, usersRepository, adminCommandService);
                this.infosService = infosService;
                this.announcementsService = announcementsService;
                this.applicationsService = applicationsService;
                this.notificationsService = notificationsService;
        }

        public MatrixMCPModels.MCPToolResult getInfos(MatrixAssistantAuthContext authContext) {
                Locale locale = getLocaleFromAuthContext(authContext);

                try {
                        com.neohoods.portal.platform.model.Info info = infosService.getInfos().block();
                        if (info == null) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(true)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.infos.none", locale))
                                                                .build()))
                                                .build();
                        }

                        List<String> results = new ArrayList<>();
                        results.add(translate("matrix.mcp.infos.title", locale));

                        if (info.getNextAGDate() != null) {
                                results.add("\n📅 " + translate("matrix.mcp.infos.nextAG", locale) + ": "
                                                + info.getNextAGDate());
                        }
                        if (info.getRulesUrl() != null && !info.getRulesUrl().isEmpty()) {
                                results.add("📋 " + translate("matrix.mcp.infos.rules", locale) + ": "
                                                + info.getRulesUrl());
                        }

                        if (info.getDelegates() != null && !info.getDelegates().isEmpty()) {
                                results.add("\n👥 " + translate("matrix.mcp.infos.delegates", locale) + ":");
                                for (com.neohoods.portal.platform.model.Delegate delegate : info.getDelegates()) {
                                        StringBuilder delegateInfo = new StringBuilder("- ");
                                        if (delegate.getFirstName() != null) {
                                                delegateInfo.append(delegate.getFirstName());
                                        }
                                        if (delegate.getLastName() != null) {
                                                delegateInfo.append(" ").append(delegate.getLastName());
                                        }
                                        if (delegate.getEmail() != null) {
                                                delegateInfo.append(" (").append(delegate.getEmail()).append(")");
                                        }
                                        results.add(delegateInfo.toString());
                                }
                        }

                        if (info.getContactNumbers() != null && !info.getContactNumbers().isEmpty()) {
                                results.add("\n📞 " + translate("matrix.mcp.infos.contacts", locale) + ":");
                                for (com.neohoods.portal.platform.model.ContactNumber contact : info
                                                .getContactNumbers()) {
                                        StringBuilder contactInfo = new StringBuilder("- ");
                                        contactInfo.append(contact.getName() != null ? contact.getName()
                                                        : contact.getType());
                                        if (contact.getPhoneNumber() != null) {
                                                contactInfo.append(": ").append(contact.getPhoneNumber());
                                        }
                                        if (contact.getEmail() != null) {
                                                contactInfo.append(" - ")
                                                                .append(translate("matrix.mcp.emergencyNumbers.email",
                                                                                locale))
                                                                .append(": ").append(contact.getEmail());
                                        }
                                        if (contact.getAddress() != null) {
                                                contactInfo.append(" - ")
                                                                .append(translate("matrix.mcp.emergencyNumbers.address",
                                                                                locale))
                                                                .append(": ").append(contact.getAddress());
                                        }
                                        results.add(contactInfo.toString());
                                }
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(String.join("\n", results))
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting infos: {}", e.getMessage(), e);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.infos.error", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult getAnnouncements(Map<String, Object> arguments,
                        MatrixAssistantAuthContext authContext) {
                try {
                        Integer page = arguments.get("page") != null ? (Integer) arguments.get("page") : 1;
                        Integer pageSize = arguments.get("pageSize") != null ? (Integer) arguments.get("pageSize")
                                        : 10;

                        com.neohoods.portal.platform.model.PaginatedAnnouncementsResponse response = announcementsService
                                        .getAnnouncementsPaginated(page, pageSize)
                                        .block();

                        if (response == null || response.getAnnouncements() == null
                                        || response.getAnnouncements().isEmpty()) {
                                Locale locale = getLocaleFromAuthContext(authContext);
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.announcements.none",
                                                                                locale))
                                                                .build()))
                                                .build();
                        }

                        List<String> results = new ArrayList<>();
                        Locale locale = getLocaleFromAuthContext(authContext);
                        results.add(translate("matrix.mcp.announcements.title", locale));
                        for (com.neohoods.portal.platform.model.Announcement announcement : response
                                        .getAnnouncements()) {
                                results.add("\n📢 " + announcement.getTitle());
                                if (announcement.getContent() != null) {
                                        results.add(announcement.getContent());
                                }
                                if (announcement.getCategory() != null) {
                                        results.add(translate("matrix.mcp.announcements.category", locale) + ": "
                                                        + announcement.getCategory().getValue());
                                }
                                if (announcement.getCreatedAt() != null) {
                                        results.add(translate("matrix.mcp.announcements.date", locale) + ": "
                                                        + announcement.getCreatedAt());
                                }
                                results.add("---");
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(String.join("\n", results))
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting announcements: {}", e.getMessage(), e);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.announcements.error", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult getApplications(MatrixAssistantAuthContext authContext) {
                try {
                        List<com.neohoods.portal.platform.model.Application> applications = applicationsService
                                        .getApplications()
                                        .collectList()
                                        .block();

                        Locale locale = getLocaleFromAuthContext(authContext);
                        if (applications == null || applications.isEmpty()) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.applications.none", locale))
                                                                .build()))
                                                .build();
                        }

                        List<String> results = new ArrayList<>();
                        results.add(translate("matrix.mcp.applications.title", locale));
                        for (com.neohoods.portal.platform.model.Application app : applications) {
                                if (app.getDisabled() != null && app.getDisabled()) {
                                        continue; // Skip disabled apps
                                }
                                results.add("\n📱 " + app.getName());
                                if (app.getUrl() != null) {
                                        results.add(translate("matrix.mcp.applications.url", locale) + ": "
                                                        + app.getUrl());
                                }
                                if (app.getHelpText() != null) {
                                        results.add(translate("matrix.mcp.applications.description", locale) + ": "
                                                        + app.getHelpText());
                                }
                                results.add("---");
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(String.join("\n", results))
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting applications: {}", e.getMessage(), e);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.applications.error", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult getNotifications(MatrixAssistantAuthContext authContext) {
                try {
                        // getNotifications requires DM (personal data)
                        UserEntity user = authContext.getAuthenticatedUser();
                        List<com.neohoods.portal.platform.model.Notification> notifications = notificationsService
                                        .getNotifications(user.getId())
                                        .collectList()
                                        .block();

                        Locale locale = getLocaleFromAuthContext(authContext);
                        if (notifications == null || notifications.isEmpty()) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.notifications.none",
                                                                                locale))
                                                                .build()))
                                                .build();
                        }

                        List<String> results = new ArrayList<>();
                        results.add(translate("matrix.mcp.notifications.title", locale));
                        for (com.neohoods.portal.platform.model.Notification notification : notifications) {
                                results.add("\n🔔 " + (notification.getAuthor() != null ? notification.getAuthor()
                                                : translate("matrix.mcp.notifications.system", locale)));
                                if (notification.getPayload() != null) {
                                        Object title = notification.getPayload().get("title");
                                        if (title != null) {
                                                results.add(translate("matrix.mcp.notifications.title", locale) + ": "
                                                                + title);
                                        }
                                        Object content = notification.getPayload().get("content");
                                        if (content != null) {
                                                results.add(translate("matrix.mcp.notifications.content", locale) + ": "
                                                                + content);
                                        }
                                }
                                if (notification.getDate() != null) {
                                        results.add(translate("matrix.mcp.notifications.date", locale) + ": "
                                                        + notification.getDate());
                                }
                                results.add(translate("matrix.mcp.notifications.read", locale) + ": " + (notification
                                                .getAlreadyRead() != null
                                                && notification.getAlreadyRead()
                                                                ? translate("matrix.mcp.notifications.yes", locale)
                                                                : translate("matrix.mcp.notifications.no", locale)));
                                results.add("---");
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(String.join("\n", results))
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting notifications: {}", e.getMessage(), e);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.notifications.error", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult getUnreadNotificationsCount(MatrixAssistantAuthContext authContext) {
                try {
                        // getUnreadNotificationsCount requires DM (personal data)
                        UserEntity user = authContext.getAuthenticatedUser();
                        com.neohoods.portal.platform.model.GetUnreadNotificationsCount200Response response = notificationsService
                                        .getUnreadNotificationsCount(user.getId())
                                        .block();

                        Locale locale = getLocaleFromAuthContext(authContext);
                        if (response == null) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.notifications.unreadCount",
                                                                                locale, 0))
                                                                .build()))
                                                .build();
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.notifications.unreadCount", locale,
                                                                        response.getCount()))
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting unread notifications count: {}", e.getMessage(), e);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.notifications.unreadCountError",
                                                                        locale, e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }

        public MatrixMCPModels.MCPToolResult getUsers(MatrixAssistantAuthContext authContext) {
                try {
                        List<UserEntity> users = new ArrayList<>();
                        usersRepository.findAll().forEach(users::add);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        if (users == null || users.isEmpty()) {
                                return MatrixMCPModels.MCPToolResult.builder()
                                                .isError(false)
                                                .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                                .type("text")
                                                                .text(translate("matrix.mcp.users.none", locale))
                                                                .build()))
                                                .build();
                        }

                        List<String> results = new ArrayList<>();
                        results.add(translate("matrix.mcp.users.title", locale));
                        for (UserEntity user : users) {
                                StringBuilder userInfo = new StringBuilder("\n👤 ");
                                if (user.getFirstName() != null) {
                                        userInfo.append(user.getFirstName());
                                }
                                if (user.getLastName() != null) {
                                        userInfo.append(" ").append(user.getLastName());
                                }
                                if (user.getEmail() != null) {
                                        userInfo.append(" (").append(user.getEmail()).append(")");
                                }
                                if (user.getType() != null) {
                                        userInfo.append(" - ").append(translate("matrix.mcp.users.type", locale))
                                                        .append(": ").append(user.getType());
                                }
                                results.add(userInfo.toString());
                        }

                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(false)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(String.join("\n", results))
                                                        .build()))
                                        .build();
                } catch (Exception e) {
                        log.error("Error getting users: {}", e.getMessage(), e);
                        Locale locale = getLocaleFromAuthContext(authContext);
                        return MatrixMCPModels.MCPToolResult.builder()
                                        .isError(true)
                                        .content(List.of(MatrixMCPModels.MCPContent.builder()
                                                        .type("text")
                                                        .text(translate("matrix.mcp.users.error", locale,
                                                                        e.getMessage()))
                                                        .build()))
                                        .build();
                }
        }
}
