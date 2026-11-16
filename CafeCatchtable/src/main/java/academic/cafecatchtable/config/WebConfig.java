package academic.cafecatchtable.config;

import academic.cafecatchtable.auth.LoginCheckInterceptor;
import academic.cafecatchtable.auth.LoginUserArgumentResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 로그인 없이 쓸 수 있는 API (API 명세서 1장 "인증") */
    private static final String[] PUBLIC_APIS = {
            "/api/auth/signup",
            "/api/auth/check-id",
            "/api/auth/login"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginCheckInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_APIS);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginUserArgumentResolver());
    }

    /** NFR-03: 비밀번호 BCrypt 저장 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
