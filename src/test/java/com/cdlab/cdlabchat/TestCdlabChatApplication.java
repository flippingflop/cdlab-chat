package com.cdlab.cdlabchat;

import org.springframework.boot.SpringApplication;

public class TestCdlabChatApplication {

    public static void main(String[] args) {
        SpringApplication.from(CdlabChatApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
