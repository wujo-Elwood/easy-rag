package com.rag.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 * 负责 JWT Token 的生成、解析、验证
 * 使用 HMAC-SHA256 签名算法
 */
@Component
public class JwtUtils {

    /** JWT 签名密钥，从配置文件读取 */
    @Value("${jwt.secret}")
    private String secret;

    /** Token 有效期（毫秒），从配置文件读取 */
    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * 获取签名密钥对象
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT Token
     * Token 中包含 userId（subject）和 username（claim）
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return JWT Token 字符串
     */
    public String generateToken(Long userId, String username) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 JWT Token，返回 Claims 对象
     *
     * @param token JWT Token 字符串
     * @return Claims 对象（包含 subject、claims、expiration 等）
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中提取用户ID
     *
     * @param token JWT Token
     * @return 用户ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 中提取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 判断 Token 是否已过期
     *
     * @param token JWT Token
     * @return true=已过期，false=未过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 验证 Token 是否有效（未过期且签名正确）
     *
     * @param token JWT Token
     * @return true=有效，false=无效
     */
    public boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
