package com.neohoods.portal.platform.api.hub.infos;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import com.neohoods.portal.platform.api.InfosHubApiApiDelegate;
import com.neohoods.portal.platform.model.Info;
import com.neohoods.portal.platform.services.InfosService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class InfosHubApi implements InfosHubApiApiDelegate {

    private final InfosService infosService;

    @Override
    public Mono<ResponseEntity<Info>> getInfos(ServerWebExchange exchange) {
        return infosService.getInfos()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error retrieving community infos", e);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Mono<ResponseEntity<Void>> updateInfos(Mono<Info> info, ServerWebExchange exchange) {
        return (Mono) info
                .flatMap(infosService::updateInfos)
                .then(Mono.fromSupplier(() -> {
                    log.info("Community infos updated successfully");
                    return ResponseEntity.ok().build();
                }))
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
}
