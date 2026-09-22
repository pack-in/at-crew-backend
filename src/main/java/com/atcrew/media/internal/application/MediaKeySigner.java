package com.atcrew.media.internal.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 업로드 key에 소유자 서명을 넣고 확인한다(#190).
 *
 * <p>presign이 발급하는 key는 공개 API 응답에 그대로 실린다. 예전에는 key가 `raw/&lt;uuid&gt;.jpg`뿐이라, 남의
 * key를 수집해 자기 작품의 지정 썸네일로 넣고 그 작품을 영구 삭제하면 <b>남의 R2 파일이 지워졌다.</b> 그래서
 * 발급 시점에 "누구에게 준 key인지"를 key 자체에 새긴다.
 *
 * <p>형식은 {@code raw/{서명}/{uuid}.{확장자}}다. 서명은 {@code HMAC(secret, memberId + ":" + uuid)}의 앞부분이라
 * 같은 회원이라도 key마다 다르다 — 회원마다 고정된 값이면 공개된 key를 모아 같은 업로더의 파일을 묶어볼 수 있다.
 *
 * <p>검증은 문자열을 잘라 다시 계산하는 것뿐이라 조회가 없다. Worker가 만든 변형본(`thumb/…`, `original/…`)에는
 * 서명이 없으므로 클라이언트가 제출하면 자동으로 걸린다.
 *
 * <p>비밀값을 바꿀 때는 이전 값을 {@code previous-secrets}에 남긴다. 지우면 그 전에 발급된 key가 전부 검증에
 * 실패해 수정 요청이 막힌다.
 */
@Component
public class MediaKeySigner {

    static final String PREFIX = "raw/";
    /** 서명 길이(base64url 문자 수). 12자 = 72비트 — 남이 맞혀서 통과시킬 수 없을 만큼이면 충분하다. */
    private static final int SIGNATURE_LENGTH = 12;
    private static final String ALGORITHM = "HmacSHA256";

    private final List<SecretKeySpec> keys;

    MediaKeySigner(@Value("${media.key-signature.secret}") String secret,
                   @Value("${media.key-signature.previous-secrets:}") List<String> previousSecrets) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("media.key-signature.secret이 비어 있다 — 업로드 key의 소유를 검증할 수 없다.");
        }
        List<SecretKeySpec> all = new ArrayList<>();
        all.add(keyOf(secret));
        previousSecrets.stream().filter(s -> s != null && !s.isBlank()).map(MediaKeySigner::keyOf).forEach(all::add);
        this.keys = List.copyOf(all);
    }

    /** 발급 시 쓰는 key — 서명은 현재 비밀값으로만 만든다. */
    public String sign(String memberId, String uuid) {
        return signature(keys.getFirst(), memberId, uuid);
    }

    /** {@code key}가 이 회원에게 발급된 것인지. 형식이 다르거나 서명이 맞지 않으면 false. */
    public boolean isOwnedBy(String key, String memberId) {
        if (key == null || memberId == null || !key.startsWith(PREFIX)) {
            return false;
        }
        String[] parts = key.substring(PREFIX.length()).split("/");
        if (parts.length != 2) {
            return false;
        }
        String signature = parts[0];
        String uuid = stripExtension(parts[1]);
        if (signature.isEmpty() || uuid.isEmpty()) {
            return false;
        }
        // 비밀값을 교체한 직후에는 이전 값으로 서명된 key가 살아 있다 — 등록된 값을 모두 시도한다.
        return keys.stream().anyMatch(k -> MessageDigest.isEqual(
                signature(k, memberId, uuid).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)));
    }

    private static String signature(SecretKeySpec key, String memberId, String uuid) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((memberId + ":" + uuid).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, SIGNATURE_LENGTH);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("업로드 key 서명에 실패했다", e);
        }
    }

    private static SecretKeySpec keyOf(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
