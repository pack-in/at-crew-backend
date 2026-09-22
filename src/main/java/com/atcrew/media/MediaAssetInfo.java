package com.atcrew.media;

/**
 * @param originalKey 업로드된 raw 원본의 key. <b>처리가 끝나면 R2에서 실제 객체가 삭제된다</b> — 변환 결과가
 *                    원본을 대체하기 때문이다. 식별·정리 용도로만 남는 값이므로 이미지를 불러오는 데 쓰면 안 된다
 *                    (표시용은 originalAvifKey·thumbKey).
 * @param ordinal     소유자 안에서의 순서(0부터).
 * @param slotRole    소유자가 정한 슬롯 이름. 없으면 null({@link MediaAssetSpec} 참고).
 */
public record MediaAssetInfo(String originalKey, String thumbKey, String thumbAdultKey,
                             String originalAvifKey, MediaProcessingStatus status,
                             int ordinal, String slotRole) { }
