package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        작품 이미지 한 장의 변환 처리 상태.
        - PENDING: 처리 대기·진행 중. 썸네일·AVIF key가 아직 null이다.
        - DONE: 처리 완료. thumbKey·originalAvifKey 등이 채워진다.
        - FAILED: 처리 실패. 다른 이미지가 성공했다면 작품은 부분 실패를 허용해 READY가 된다.
        작품의 모든 이미지가 PENDING을 벗어나고 하나 이상 DONE이면 작품 상태가 READY로 전이한다.""",
        example = "DONE")
public enum ImageProcessingStatus {
    PENDING, DONE, FAILED
}
