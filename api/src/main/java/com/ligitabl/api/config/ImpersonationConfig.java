package com.ligitabl.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ligitabl.api.auth.impersonation.ImpersonationSessionFilter;
import com.ligitabl.model.repo.UserRepo;

@Configuration
public class ImpersonationConfig {

    /**
     * A {@code @Bean}, not {@code @Component}: {@code @WebMvcTest} slices instantiate component-scanned
     * {@code Filter}s but skip plain {@code @Configuration} classes, so this keeps the filter and its
     * {@link UserRepo} dependency out of MVC slice tests.
     */
    @Bean
    public ImpersonationSessionFilter impersonationSessionFilter(UserRepo userRepo) {
        return new ImpersonationSessionFilter(userRepo);
    }
}
