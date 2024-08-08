package com.serg.bash.betsparser.service;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class ReportService implements InitializingBean {

    private final ParserService parserService;

    @Autowired
    public ReportService(ParserService parserService) {
        this.parserService = parserService;
    }

    @Override
    public void afterPropertiesSet() {
        System.out.println(parserService.parse().blockFirst());
    }
}
