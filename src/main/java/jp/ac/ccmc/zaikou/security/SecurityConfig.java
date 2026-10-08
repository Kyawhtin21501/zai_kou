package jp.ac.ccmc.zaikou.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {
    @Bean
    UserDetailsService users(@Value("${app.admin.username}") String username, @Value("${app.admin.password}") String password) {
        if (!username.matches("[A-Za-z0-9_-]{1,64}") || password.length() < 16 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("Set APP_ADMIN_USERNAME and a 16-72 byte APP_ADMIN_PASSWORD before starting.");
        return new InMemoryUserDetailsManager(User.withUsername(username).password(new BCryptPasswordEncoder(12).encode(password)).roles("ADMIN").build());
    }
    @Bean
    BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
            .csrf(Customizer.withDefaults())
            .cors(cors -> cors.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api/auth/csrf", "/api/auth/login").permitAll()
                .requestMatchers("/api/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .successHandler((req,res,auth) -> res.setStatus(204))
                .failureHandler((req,res,ex) -> jsonError(res,401,"ユーザー名またはパスワードが正しくありません。")))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req,res,ex) -> jsonError(res,401,"ログインしてください。"))
                .accessDeniedHandler((req,res,ex) -> jsonError(res,403,"操作が許可されていないか、セッションが更新されました。画面を再読み込みしてください。")))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'")))
            .addFilterBefore(new RequestGuardFilter(), CsrfFilter.class)
            .build();
    }
    static void jsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\",\"fields\":{}}");
    }
}
