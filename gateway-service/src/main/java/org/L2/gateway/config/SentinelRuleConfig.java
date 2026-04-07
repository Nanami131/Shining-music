package org.L2.gateway.config;

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
        Set<GatewayFlowRule> rules = new HashSet<>();

        rules.add(new GatewayFlowRule("user-service")
                .setCount(50)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("music-service")
                .setCount(100)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("community-service")
                .setCount(80)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("recommendation-service")
                .setCount(30)
                .setIntervalSec(1));

        rules.add(new GatewayFlowRule("statistics-service")
                .setCount(60)
                .setIntervalSec(1));

        GatewayRuleManager.loadRules(rules);
    }
}
