package com.rag.service;

import com.rag.common.BusinessException;
import com.rag.dto.LoginRequest;
import com.rag.dto.RegisterRequest;
import com.rag.entity.SysUser;
import com.rag.mapper.UserMapper;
import com.rag.utils.JwtUtils;
import com.rag.vo.LoginResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务
 * 处理用户注册、登录等认证相关业务
 */
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtils jwtUtils;

    /** BCrypt 密码编码器，用于密码加密和验证 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户注册
     * 校验用户名唯一性 → 加密密码 → 写入数据库 → 生成 JWT Token
     *
     * @param request 注册请求（用户名、密码、昵称）
     * @return 登录响应（含 Token 和用户信息）
     */
    public LoginResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        SysUser existing = userMapper.findByUsername(request.getUsername());
        if (existing != null) {
            throw new BusinessException(400, "Username already exists");
        }

        // 创建用户，密码使用 BCrypt 加密
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        userMapper.insert(user);

        // 生成 JWT Token 并返回
        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }

    /**
     * 用户登录
     * 验证用户名存在 → 验证密码 → 生成 JWT Token
     *
     * @param request 登录请求（用户名、密码）
     * @return 登录响应（含 Token 和用户信息）
     */
    public LoginResponse login(LoginRequest request) {
        // 查找用户
        SysUser user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(401, "Invalid username or password");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "Invalid username or password");
        }

        // 生成 JWT Token 并返回
        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }

    /**
     * 根据用户ID查询用户信息
     *
     * @param id 用户ID
     * @return 用户实体
     */
    public SysUser getUserById(Long id) {
        return userMapper.findById(id);
    }
}
