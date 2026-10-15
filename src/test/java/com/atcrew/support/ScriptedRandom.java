package com.atcrew.support;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.random.RandomGenerator;

/**
 * {@code PublicCodeGenerator}에 주입해 6자 공개 코드를 결정적으로 뽑게 하는 난수원 (member-handle-design §5).
 *
 * <p>{@link #enqueue(String...)}로 넣은 코드가 순서대로 나오고, 다 쓰면 SecureRandom으로 돌아간다 — 스크립트를
 * 넣지 않은 업로드·생성은 운영과 같은 무작위 코드를 받는다.
 */
public final class ScriptedRandom implements RandomGenerator {

    // PublicCodeGenerator의 알파벳과 같은 순서여야 한다 — 어긋나면 enqueue한 코드와 다른 코드가 나와 테스트가 실패한다.
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final Deque<Integer> scripted = new ArrayDeque<>();
    private final SecureRandom fallback = new SecureRandom();

    public synchronized void enqueue(String... codes) {
        for (String code : codes) {
            code.chars().forEach(c -> scripted.add(ALPHABET.indexOf(c)));
        }
    }

    public synchronized void clear() {
        scripted.clear();
    }

    @Override
    public synchronized int nextInt(int bound) {
        Integer next = scripted.poll();
        return next != null ? next : fallback.nextInt(bound);
    }

    @Override
    public long nextLong() {
        return fallback.nextLong();
    }
}
