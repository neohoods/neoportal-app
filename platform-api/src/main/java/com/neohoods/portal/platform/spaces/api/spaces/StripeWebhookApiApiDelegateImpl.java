package com.neohoods.portal.platform.spaces.api.spaces;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.StripeWebhookApiApiDelegate;
import com.neohoods.portal.platform.spaces.services.StripeService;

import reactor.core.publisher.Mono;

@Service
public class StripeWebhookApiApiDelegateImpl implements StripeWebhookApiApiDelegate {

    @Autowired
    private StripeService stripeService;

    @Override
    public Mono<ResponseEntity<Void>> handleStripeWebhook(
            Mono<Object> body, ServerWebExchange exchange) {

        return body.flatMap(payload -> {
            try {
                // Convert payload to String
                String payloadString = payload.toString();

                // Get signature from headers
                String signature = exchange.getRequest().getHeaders().getFirst("Stripe-Signature");
                if (signature == null) {
                    return Mono.just(ResponseEntity.badRequest().<Void>build());
                }

                // Verify webhook signature
                if (!stripeService.verifyWebhookSignature(payloadString, signature)) {
                    return Mono.just(ResponseEntity.badRequest().<Void>build());
                }

                // Process the webhook event
                boolean processed = stripeService.processWebhookEvent(payloadString);

                if (processed) {
                    return Mono.just(ResponseEntity.ok().<Void>build());
                } else {
                    return Mono.just(ResponseEntity.badRequest().<Void>build());
                }

            } catch (Exception e) {
                // Log error and return 500
                return Mono.just(ResponseEntity.internalServerError().<Void>build());
            }
        });
    }
}
