package com.atcrew.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebMvcConfig implements WebMvcConfigurer {
    // CORS는 SecurityConfig의 CorsConfigurationSource Bean이 처리
}
