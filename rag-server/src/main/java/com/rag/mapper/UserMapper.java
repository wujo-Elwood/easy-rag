package com.rag.mapper;

import com.rag.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 Mapper 接口
 * 提供用户的增查操作
 */
@Mapper
public interface UserMapper {

    /** 根据用户ID查询用户信息 */
    SysUser findById(@Param("id") Long id);

    /** 根据用户名查询用户信息（登录时使用） */
    SysUser findByUsername(@Param("username") String username);

    /** 新增用户 */
    int insert(SysUser user);
}
