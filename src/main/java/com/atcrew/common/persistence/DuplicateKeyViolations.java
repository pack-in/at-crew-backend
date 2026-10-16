package com.atcrew.common.persistence;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;

/**
 * 무결성 위반 중 <b>중복 키(UNIQUE·PK)</b>만 골라내는 판별기.
 *
 * <p>JPA {@code saveAndFlush}에서 MariaDB 중복 키는 Hibernate {@code ConstraintViolationException}을 거쳐 Spring
 * {@link DataIntegrityViolationException}으로 번역된다 — {@link DuplicateKeyException}(JDBC 경로 번역)으로 오지 않는다.
 * 그래서 원인 체인의 {@link SQLException} 에러코드로 판별한다. NOT NULL·FK·길이 초과 같은 다른 무결성 위반은
 * 중복으로 오인해 삼키지 않고 호출부가 그대로 전파하게 한다.
 */
public final class DuplicateKeyViolations {

    // MariaDB/MySQL ER_DUP_ENTRY — UNIQUE 인덱스와 PRIMARY KEY 중복 모두 이 코드다.
    private static final int ER_DUP_ENTRY = 1062;

    private DuplicateKeyViolations() {
    }

    public static boolean isDuplicateKey(DataIntegrityViolationException e) {
        if (e instanceof DuplicateKeyException) {
            return true;
        }
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == ER_DUP_ENTRY) {
                return true;
            }
        }
        return false;
    }
}
