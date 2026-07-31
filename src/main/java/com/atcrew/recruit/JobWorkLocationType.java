package com.atcrew.recruit;

// TeamWorkLocationType과 이름은 유사하나 별개 enum(laiteu 기술부채 재발 방지, 설계 §2.2)
public enum JobWorkLocationType {
    OFFICE,  // 사무실 출근
    REMOTE,  // 100% 재택
    HYBRID   // 출근+재택 혼합
}
