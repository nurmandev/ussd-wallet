package com.nurudeen.ussd_wallet.config;

import com.nurudeen.ussd_wallet.security.filter.RegistrationRateLimitFilter;
import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    Clock systemUtcClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RegistrationRateLimitFilter registrationRateLimitFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/v1/customers/register",
                        "/api/v1/ussd",
                        "/api/v1/ussd/simulator/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/customers/register",
                                "/api/v1/ussd",
                                "/api/v1/ussd/simulator/**",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(registrationRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    FilterRegistrationBean<RegistrationRateLimitFilter> registrationRateLimitFilterRegistration(
            RegistrationRateLimitFilter filter) {
        FilterRegistrationBean<RegistrationRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
