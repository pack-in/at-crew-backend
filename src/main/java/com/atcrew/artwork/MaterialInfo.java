package com.atcrew.artwork;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "작품에 사용한 소재 정보")
public record MaterialInfo(

        @Schema(description = "소재명", example = "겨울 배경 브러시셋")
        String name, // 소재명

        @ArraySchema(arraySchema = @Schema(description = "소재를 적용한 대상 목록",
                example = "[\"배경\", \"이펙트\"]"))
        List<String> targets, // 적용 대상 목록

        @ArraySchema(arraySchema = @Schema(description = "소재 첨부 이미지 R2 key 목록. 없으면 빈 배열",
                example = "[\"raw/019ff383-2c63-765b-af15-8a2c870a29ec.jpg\"]"))
        List<String> attachmentKeys, // 첨부 이미지 key 목록

        @ArraySchema(arraySchema = @Schema(description = "외부 소재 판매·출처 URL 목록. 없으면 빈 배열",
                example = "[\"https://www.acon3d.com/product/12345\"]"))
        List<String> links // 외부 소재 URL 목록
) {
}
