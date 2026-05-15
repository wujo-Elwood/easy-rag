package com.rag.config;

import com.rag.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置类
 * 配置跨域（CORS）和 JWT 认证拦截器
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 配置跨域策略
     * 允许前端开发服务器（5173/3000 端口）跨域访问所有接口
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 配置请求拦截器
     * 对 /api/** 路径进行 JWT 认证，/api/auth/** 路径（登录/注册）放行
     * 认证通过后将 userId 和 username 写入 request 属性，供 Controller 使用
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                // OPTIONS 预检请求直接放行
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    return true;
                }

                // 登录/注册接口不需要认证
                String path = request.getRequestURI();
                if (path.startsWith("/api/auth/")) {
                    return true;
                }

                // 解析 Authorization 头中的 JWT Token
                String token = request.getHeader("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                    if (jwtUtils.validateToken(token)) {
                        // 认证通过，将用户信息写入 request 属性
                        request.setAttribute("userId", jwtUtils.getUserId(token));
                        request.setAttribute("username", jwtUtils.getUsername(token));
                        return true;
                    }
                }

                // 认证失败，返回 401
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
        }).addPathPatterns("/api/**");
    }
}
