package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.service.user.UserCreatorService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@AllArgsConstructor
@Component
public class ApplicationStartupRunner implements CommandLineRunner {

    private final UserCreatorService userCreatorService;

    @Override
    public void run(String... args) {
        if (!userCreatorService.doesAnyAdminUserExist()) {
            userCreatorService.createAdminUser();
        }
    }
}
