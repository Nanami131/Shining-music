package org.L2.common.rpc;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.L2.common.context.RequestTraceContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/**
 * 将当前 HTTP/MQ 链路的 requestId 继续传递给下游 Feign 服务。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequestTraceFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String requestId = RequestTraceContext.current();
        if (requestId != null && !requestId.isBlank()) {
            template.header(RequestTraceContext.HEADER_NAME, requestId);
        }
    }
}
