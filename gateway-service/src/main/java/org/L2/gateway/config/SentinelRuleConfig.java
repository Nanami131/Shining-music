package org.L2.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class SentinelRuleConfig {

    @PostConstruct
    public void initGatewayRules() {

        // ── API 分组定义（按接口路径匹配） ──

        Set<ApiDefinition> apiDefs = new HashSet<>();

        apiDefs.add(new ApiDefinition("login-api").setPredicateItems(Set.of(
                new ApiPathPredicateItem()
                        .setPattern("/api/user/login")
                        .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT))));

        apiDefs.add(new ApiDefinition("recommend-cb-api").setPredicateItems(Set.of(
                new ApiPathPredicateItem()
                        .setPattern("/api/recommend/daily/content-based")
                        .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT))));

        apiDefs.add(new ApiDefinition("recommend-itemcf-api").setPredicateItems(Set.of(
                new ApiPathPredicateItem()
                        .setPattern("/api/recommend/daily/item-cf")
                        .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT))));

        apiDefs.add(new ApiDefinition("recommend-rebuild-api").setPredicateItems(Set.of(
                new ApiPathPredicateItem()
                        .setPattern("/api/recommend/itemcf/rebuild")
                        .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT))));

        GatewayApiDefinitionManager.loadApiDefinitions(apiDefs);

        // ── 限流规则 ──

        Set<GatewayFlowRule> rules = new HashSet<>();

        // 接口级限流（基于压测数据，按裸吞吐 60-70% 设定）
        rules.add(new GatewayFlowRule("login-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(200)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("recommend-cb-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(1500)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("recommend-itemcf-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(1000)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("recommend-rebuild-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(5)
                .setIntervalSec(1));

        // 服务级兜底限流
        rules.add(new GatewayFlowRule("user-service")
                .setCount(500)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("music-service")
                .setCount(3000)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("community-service")
                .setCount(500)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("recommendation-service")
                .setCount(3000)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("statistics-service")
                .setCount(5000)
                .setIntervalSec(1));

        GatewayRuleManager.loadRules(rules);
    }
}
