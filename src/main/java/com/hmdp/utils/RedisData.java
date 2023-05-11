package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 新建一个类，对原来的代码没有侵入性
 */
@Data
public class RedisData {
    //逻辑过期时间
    private LocalDateTime expireTime;
    private Object data;
}
