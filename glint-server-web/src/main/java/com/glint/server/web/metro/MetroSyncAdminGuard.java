package com.glint.server.web.metro;

import com.glint.server.biz.auth.AdminAccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 线网同步等运维接口的管理密钥校验；未配置密钥时一律拒绝。 */
@Component
public class MetroSyncAdminGuard {

    private final byte[] expectedKey;

    public MetroSyncAdminGuard(@Value("${glint.metro.sync-admin-key:}") String syncAdminKey) {
        this.expectedKey = syncAdminKey == null ? new byte[0] : syncAdminKey.getBytes(StandardCharsets.UTF_8);
    }

    public void requireAdmin(String providedKey) {
        if (expectedKey.length == 0) {
            throw new AdminAccessDeniedException();
        }
        byte[] provided = providedKey == null ? new byte[0] : providedKey.getBytes(StandardCharsets.UTF_8);
        if (provided.length != expectedKey.length || !MessageDigest.isEqual(expectedKey, provided)) {
            throw new AdminAccessDeniedException();
        }
    }
}
