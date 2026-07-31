package com.atcrew.recruit;

// JobWorkLocationType과 이름은 유사하나 별개 enum(laiteu 기술부채 재발 방지, 설계 §2.2)
public enum TeamWorkLocationType {
    OFFLINE,  // 오프라인
    ONLINE,   // 온라인
    HYBRID    // 오프라인+온라인 혼합
}
