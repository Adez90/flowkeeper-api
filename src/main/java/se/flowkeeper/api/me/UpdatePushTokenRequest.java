package se.flowkeeper.api.me;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePushTokenRequest(@NotBlank @Size(max = 200) String expoPushToken) {
}
