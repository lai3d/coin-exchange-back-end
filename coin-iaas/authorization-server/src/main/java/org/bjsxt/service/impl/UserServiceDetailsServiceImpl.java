package org.bjsxt.service.impl;

import org.bjsxt.constant.LoginConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String loginType = requestAttributes.getRequest().getParameter("login_type");
        if(StringUtils.isEmpty(loginType)) {
            throw new AuthenticationServiceException("Login type cannot be empty!");
        }
        UserDetails userDetails = null;
        try {
            switch (loginType) {
                case LoginConstant.ADMIN_TYPE:
                    userDetails = loadSysUserByUsername(username);
                    break;
                case LoginConstant.MEMBER_TYPE:
                    userDetails = loadMemberUserByUsername(username);
                    break;
                default:
                    throw new AuthenticationServiceException("Login type not supported: " + loginType);
            }
        } catch (IncorrectResultSizeDataAccessException e) {
            throw new UsernameNotFoundException("用户名" + username + "不存在");
        }

        return userDetails;
    }

    private UserDetails loadMemberUserByUsername(String username) {
        return null;
    }

    private UserDetails loadSysUserByUsername(String username) {
        // 1 使用用户名查询用户
        return jdbcTemplate.queryForObject(LoginConstant.QUERY_ADMIN_SQL, new RowMapper<User>() {
            @Override
            public User mapRow(ResultSet rs, int rowNum) throws SQLException {
                if (rs.wasNull()) {
                    throw new UsernameNotFoundException("用户名" + username + "不存在");
                }
                long id = rs.getLong("id"); // 用户的id
                String password = rs.getString("password"); // 用户的密码
                int status = rs.getInt("status");
                return new User(   // 3 封装成一个UserDetails对象，返回
                        String.valueOf(id), //使用id->username
                        password,
                        status == 1,
                        true,
                        true,
                        true,
                        getSysUserPermissions(id)
                );
            }
        }, username);
        // 2 查询这个用户对应的权限
        // 3 封装成一个UserDetails对象，返回
    }

    /**
     * // 2 查询这个用户对应的权限
     * 通过用户的id 查询用户的权限
     *
     * @param id
     * @return
     */
    private Collection<? extends GrantedAuthority> getSysUserPermissions(long id) {
        // 1 当用户为超级管理员时，他拥有所有的权限数据
        String roleCode = jdbcTemplate.queryForObject(LoginConstant.QUERY_ROLE_CODE_SQL, String.class, id);
        List<String> permissions = null; // 权限的名称
        if (LoginConstant.ADMIN_ROLE_CODE.equals(roleCode)) { // 超级用户
            permissions = jdbcTemplate.queryForList(LoginConstant.QUERY_ALL_PERMISSIONS, String.class);
        } else { // 2 普通用户，需要使用角色->权限数据
            permissions = jdbcTemplate.queryForList(LoginConstant.QUERY_PERMISSION_SQL, String.class, id);
        }
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptySet();
        }
        return permissions.stream()
                .distinct() // 去重
                .map(perm -> new SimpleGrantedAuthority(perm))
                .collect(Collectors.toSet());
    }
}
