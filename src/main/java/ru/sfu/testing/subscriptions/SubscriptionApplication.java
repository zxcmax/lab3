package ru.sfu.testing.subscriptions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SubscriptionApplication {
    public static void main(String[] args) {
        SpringApplication.run(SubscriptionApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
