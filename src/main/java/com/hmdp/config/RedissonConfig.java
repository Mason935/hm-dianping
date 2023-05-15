package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置Redisson客户端
 *
 * @author gtao
 * @date 2023/5/14 19:59
 * @since 1.0.0
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.56.10:6379");
        //创建RedissonClient对象
        return Redisson.create(config);
    }
}
