package com.glint.server.dal.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 用户私密账号数据访问接口。 */
public interface UserRepository extends JpaRepository<UserEntity, String> {

    /** 通过规范化后的邮箱查找账号。 */
    Optional<UserEntity> findByEmail(String email);

    /** 通过规范化后的手机号查找账号。 */
    Optional<UserEntity> findByPhone(String phone);
}
