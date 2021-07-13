/*
 * Copyright Siemens AG, 2017-2018. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.rest.resourceserver.security;

import org.eclipse.sw360.rest.resourceserver.security.apiToken.ApiTokenAuthenticationFilter;
import org.eclipse.sw360.rest.resourceserver.security.apiToken.ApiTokenAuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.ResourceServerProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Profile("!SECURITY_MOCK")
@Configuration
@EnableWebSecurity
@EnableResourceServer
@EnableGlobalMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
public class ResourceServerConfiguration extends WebSecurityConfigurerAdapter implements ResourceServerConfigurer {

    @Autowired
    private ApiTokenAuthenticationFilter filter;

    @Autowired
    private ApiTokenAuthenticationProvider authProvider;

    @Autowired
    private ResourceServerProperties resourceServerProperties;

    public ResourceServerConfiguration(ResourceServerProperties resourceServerProperties) {
        this.resourceServerProperties = resourceServerProperties;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder authenticationManagerBuilder) {
        authenticationManagerBuilder.authenticationProvider(this.authProvider);
    }

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) {
        resources.resourceId(resourceServerProperties.getResourceId());
    }

    @Override
    public void configure(WebSecurity web) {
        web.ignoring().antMatchers("/", "/**/*.html", "/**/*.css", "/**/*.js", "/**/*.json", "/**/*.png", "/**/*.gif");
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        // TODO Thomas Maier 15-12-2017
        // Use Sw360GrantedAuthority from authorization server

        http
                .addFilterBefore(filter, BasicAuthenticationFilter.class)
                .authenticationProvider(authProvider)
                .httpBasic()
                .and()
                .authorizeRequests()
                .antMatchers(HttpMethod.GET, "/health").permitAll()
                .antMatchers(HttpMethod.GET, "/info").hasAuthority("WRITE")
                .antMatchers(HttpMethod.GET, "/api").permitAll()
                .antMatchers(HttpMethod.GET, "/api/**").hasAuthority("READ")
                .antMatchers(HttpMethod.POST, "/api/**").hasAuthority("WRITE")
                .antMatchers(HttpMethod.PUT, "/api/**").hasAuthority("WRITE")
                .antMatchers(HttpMethod.DELETE, "/api/**").hasAuthority("WRITE")
                .antMatchers(HttpMethod.PATCH, "/api/**").hasAuthority("WRITE").and()
                .csrf().disable();
    }
}
