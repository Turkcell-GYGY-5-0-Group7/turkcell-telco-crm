package com.telco.notification.api;

import com.telco.notification.api.dto.NotificationResponse;
import com.telco.notification.domain.CommunicationPreference;
import com.telco.notification.domain.Notification;
import com.telco.notification.service.NotificationService;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final ApiResponseFactory apiResponseFactory;

    public NotificationController(NotificationService notificationService,
                                   ApiResponseFactory apiResponseFactory) {
        this.notificationService = notificationService;
        this.apiResponseFactory = apiResponseFactory;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<NotificationResponse> send(@Valid @RequestBody SendRequest request) {
        Notification notification = notificationService.dispatch(
                request.userId(), request.templateCode(), request.channel(),
                request.variables() != null ? request.variables() : Map.of(), "en");
        return apiResponseFactory.ok(NotificationResponse.from(notification));
    }

    @GetMapping("/users/{userId}/history")
    @PreAuthorize("hasRole('ADMIN') or #userId == @currentUserProvider.currentUser().customerId()")
    public ApiResult<PageResult<NotificationResponse>> history(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        Page<Notification> notifications = notificationService.history(userId, page, size, sort);
        List<NotificationResponse> content = notifications.getContent().stream()
                .map(NotificationResponse::from).toList();
        return apiResponseFactory.ok(new PageResult<>(content, page, size,
                notifications.getTotalElements(), notifications.getTotalPages()));
    }

    @GetMapping("/users/{userId}/preferences")
    @PreAuthorize("hasRole('ADMIN') or #userId == @currentUserProvider.currentUser().customerId()")
    public ApiResult<List<CommunicationPreference>> getPreferences(@PathVariable String userId) {
        return apiResponseFactory.ok(notificationService.getPreferences(userId));
    }

    @PutMapping("/users/{userId}/preferences/{channel}")
    @PreAuthorize("hasRole('ADMIN') or #userId == @currentUserProvider.currentUser().customerId()")
    public ApiResult<CommunicationPreference> updatePreference(
            @PathVariable String userId,
            @PathVariable String channel,
            @Valid @RequestBody PreferenceRequest request) {
        return apiResponseFactory.ok(notificationService.upsertPreference(
                userId, channel.toUpperCase(), request.optedIn()));
    }

    public record SendRequest(
            @NotBlank String userId,
            @NotBlank String templateCode,
            @NotBlank String channel,
            Map<String, String> variables) {}

    public record PreferenceRequest(boolean optedIn) {}
}
