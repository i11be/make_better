package com.makemebetter.config;

import com.makemebetter.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 全站只有一个账号，就是库里的第一行。 */
    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findFirstByOrderByIdAsc()
                .filter(user -> user.getUsername().equals(username))
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .roles("OWNER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("没有这个账号"));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService userDetailsService)
            throws Exception {
        http
                // 整站都在门后：除了登录、首次设置和静态资源，什么都得先登录
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/setup", "/error").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.svg").permitAll()
                        .anyRequest().hasRole("OWNER"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        // 登录成功一律回到世界树，并让它弹一句「身份核验成功」
                        .successHandler((request, response, authentication) ->
                                response.sendRedirect("/?ok=login"))
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .userDetailsService(userDetailsService);
        return http.build();
    }
}
