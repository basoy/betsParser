package com.serg.bash.betsparser;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
@PropertySource("classpath:application.properties")
public class AppConfig {

    @Bean
    public Scheduler scheduler() {
        return Schedulers.newParallel("proxy-service-pool", 3);
    }
}
