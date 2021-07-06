/*
 * Copyright Siemens AG, 2017, 2019. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.rest.authserver.security;

import org.eclipse.sw360.datahandler.thrift.ThriftClients;
import org.eclipse.sw360.rest.authserver.security.basicauth.Sw360LiferayAuthenticationProvider;
import org.eclipse.sw360.rest.authserver.security.customheaderauth.Sw360CustomHeaderAuthenticationFilter;
import org.eclipse.sw360.rest.authserver.security.customheaderauth.Sw360CustomHeaderAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

/**
 * This class configures the standard spring security for this server. Some of
 * the created beans are also necessary in the special oauth 2 flows, so they
 * are injected and used in the second security config class
 * {@link Sw360AuthorizationServerConfiguration}.
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class Sw360WebSecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS)
                    .permitAll() // some JS frameworks make HTTP OPTIONS requests
                .anyRequest()
                    .authenticated()
                .and()
                .addFilterBefore(sw360CustomHeaderAuthenticationFilter(),
                        AbstractPreAuthenticatedProcessingFilter.class)
                .httpBasic()
                .and()
                    .csrf().disable();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder authenticationManagerBuilder) {
        authenticationManagerBuilder
                .authenticationProvider(sw360LiferayAuthenticationProvider())
                .authenticationProvider(sw360CustomHeaderAuthenticationProvider());
    }

    /**
     * We have to publish our configured authentication manager because otherwise
     * some boot default manager will be populated from
     * {@link AuthenticationManagerConfiguration}.
     */
    @Bean(name = "authenticationManager")
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Bean
    protected Sw360CustomHeaderAuthenticationFilter sw360CustomHeaderAuthenticationFilter() {
        return new Sw360CustomHeaderAuthenticationFilter();
    }

    @Bean
    protected Sw360CustomHeaderAuthenticationProvider sw360CustomHeaderAuthenticationProvider() {
        return new Sw360CustomHeaderAuthenticationProvider();
    }

    @Bean
    protected Sw360LiferayAuthenticationProvider sw360LiferayAuthenticationProvider() {
        return new Sw360LiferayAuthenticationProvider();
    }

    @Bean
    protected Sw360UserDetailsProvider principalProvider() {
        return new Sw360UserDetailsProvider();
    }

    @Bean
    protected ThriftClients thriftClients() {
        return new ThriftClients();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}
