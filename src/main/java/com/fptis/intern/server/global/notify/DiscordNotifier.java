package com.fptis.intern.server.global.notify;

import com.fptis.intern.server.global.config.NotifyProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordNotifier {

    private final WebClient notifyWebClient;
    private final NotifyProperties notifyProperties;

    public void send(String message) {
        String webhookUrl = notifyProperties.discordWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("[DiscordNotifier] Webhook URL 미설정, 알림 전송하지 않습니다.");
            return;
        }

        notifyWebClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", message))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error -> log.error("[DiscordNotifier] 알림 전송 실패", error))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}
