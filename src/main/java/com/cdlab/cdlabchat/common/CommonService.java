package com.cdlab.cdlabchat.common;

import org.springframework.stereotype.Service;

@Service
public class CommonService {

    private final CommonRepository commonRepository;

    public CommonService(CommonRepository commonRepository) {
        this.commonRepository = commonRepository;
    }

    public int health() {
        return commonRepository.ping();
    }
}
