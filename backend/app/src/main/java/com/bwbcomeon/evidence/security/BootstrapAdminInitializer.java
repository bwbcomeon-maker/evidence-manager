package com.bwbcomeon.evidence.security;

import com.bwbcomeon.evidence.entity.SysUser;
import com.bwbcomeon.evidence.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 首个系统管理员安全引导：仅在显式配置时创建/恢复 admin，替代固定默认口令。
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final SysUserMapper sysUserMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${security.bootstrap-admin.enabled:false}")
    private boolean enabled;

    @Value("${security.bootstrap-admin.username:}")
    private String username;

    @Value("${security.bootstrap-admin.password:}")
    private String password;

    @Value("${security.bootstrap-admin.real-name:系统管理员}")
    private String realName;

    public BootstrapAdminInitializer(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (sysUserMapper.countEnabledByRoleCodeAndNotDeleted("SYSTEM_ADMIN") > 0) {
            logger.info("Bootstrap admin skipped: active system admin already exists");
            return;
        }
        String user = username == null ? "" : username.trim();
        String pwd = password == null ? "" : password.trim();
        if (user.isEmpty() || pwd.length() < MIN_PASSWORD_LENGTH) {
            logger.error("Bootstrap admin skipped: configure security.bootstrap-admin.username/password and ensure password length >= 8");
            return;
        }

        SysUser existing = sysUserMapper.selectByUsername(user);
        String passwordHash = passwordEncoder.encode(pwd);
        if (existing != null) {
            existing.setPasswordHash(passwordHash);
            existing.setRoleCode("SYSTEM_ADMIN");
            existing.setEnabled(true);
            existing.setRealName(realName);
            existing.setUpdatedAt(LocalDateTime.now());
            sysUserMapper.update(existing);
            logger.warn("Bootstrap admin restored existing account: {}", user);
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername(user);
        admin.setPasswordHash(passwordHash);
        admin.setRealName(realName);
        admin.setRoleCode("SYSTEM_ADMIN");
        admin.setEnabled(true);
        admin.setIsDeleted(false);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(admin);
        logger.warn("Bootstrap admin created: {}", user);
    }
}
