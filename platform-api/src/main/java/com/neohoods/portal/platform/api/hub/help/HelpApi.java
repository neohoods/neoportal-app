package com.neohoods.portal.platform.api.hub.help;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.HelpApiApiDelegate;
import com.neohoods.portal.platform.model.HelpArticle;
import com.neohoods.portal.platform.model.HelpCategory;
import com.neohoods.portal.platform.services.HelpService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class HelpApi implements HelpApiApiDelegate {

    private final HelpService helpService;

    @Override
    public Mono<ResponseEntity<Flux<HelpArticle>>> getHelpArticles(String categoryId, ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(helpService.getHelpArticles(categoryId)));
    }

    @Override
    public Mono<ResponseEntity<Flux<HelpCategory>>> getHelpCategories(ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(helpService.getHelpCategories()));
    }
}