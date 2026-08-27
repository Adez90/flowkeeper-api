package se.flowkeeper.api.me;

public record UpdateNotificationPreferencesRequest(boolean notifyInApp, boolean notifyPush, boolean notifyEmail) {
}
