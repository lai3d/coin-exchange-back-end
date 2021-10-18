package com.bjsxt.service;

import com.bjsxt.model.WebLog;

public interface TestService {
    /**
     * 通过username 查询weblog
     *
     */
    WebLog get(String username);
}
