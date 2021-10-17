package com.bjsxt.filter;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
@Slf4j
public class JwtCheckFilter implements GlobalFilter, Ordered {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${no.require.urls:/admin/login}")
    private Set<String> noTokenAccessUris;

    /**
     * 过滤器拦截到用户的请求后做啥
     * @param exchange
     * @param chain
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("===== filter =====");
        // 1. 该接口是否需要token才能访问
        if(allowNoTokenAccess(exchange)) {
            return chain.filter(exchange); // 不需要token, 直接放行
        }
        log.info("will get user token");
        // 2. 取出用户的token
        String token = getUserToken(exchange);
        // 3. 判断用户的token是否有效
        if(StringUtils.isEmpty(token)) {
            return buildNoAuthorizationResult(exchange);
        }
        Boolean hasKey = redisTemplate.hasKey(token);
        if(hasKey != null && hasKey) {
            return chain.filter(exchange); // token有效，直接放行
        }
        return buildNoAuthorizationResult(exchange);
    }

    private Mono<Void> buildNoAuthorizationResult(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set("Content-Type", "application/json");
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("error", "NoAuthorization");
        jsonObject.put("errorMsg", "Token is Null or Error");
        DataBuffer wrap = response.bufferFactory().wrap(jsonObject.toJSONString().getBytes());
        return response.writeWith(Flux.just(wrap));
    }

    /**
     * 从请求头里面获取用户的token
     * @param exchange
     * @return
     */
    private String getUserToken(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return token == null ? null : token.replace("bearer", "");
    }

    private boolean allowNoTokenAccess(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        log.info(path);
        if(noTokenAccessUris.contains(path)) {
            return true;
        }
        return false;
    }

    /**
     * 拦截器的顺序
     * This one is necessary!!!!!
     * @return
     */
    @Override
    public int getOrder() {
        return 0;
    }
}
