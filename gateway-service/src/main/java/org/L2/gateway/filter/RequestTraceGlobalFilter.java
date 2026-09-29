package org.L2.gateway.filter;

import org.L2.common.context.RequestTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.concurrent.TimeUnit;

/**
 * Gateway 入口链路日志。显式携带 requestId，避免在 WebFlux 线程切换中误用 ThreadLocal MDC。
 */
@Component
public class RequestTraceGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestTraceGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = RequestTraceContext.resolveOrCreate(
                exchange.getRequest().getHeaders().getFirst(RequestTraceContext.HEADER_NAME));
        long startedAt = System.nanoTime();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(RequestTraceContext.HEADER_NAME, requestId))
                .build();
        ServerWebExchange tracedExchange = exchange.mutate().request(request).build();
        tracedExchange.getResponse().getHeaders().set(RequestTraceContext.HEADER_NAME, requestId);

        return chain.filter(tracedExchange)
                .doFinally(signalType -> logRequest(tracedExchange, requestId, startedAt, signalType));
    }

    private void logRequest(ServerWebExchange exchange, String requestId, long startedAt, SignalType signalType) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        int status = statusCode != null
                ? statusCode.value()
                : (signalType == SignalType.ON_ERROR ? 500 : 200);
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "-";

        log.info("gateway_request requestId={} method={} path={} status={} durationMs={} route={} signal={}",
                requestId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath(),
                status,
                durationMs,
                routeId,
                signalType);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
