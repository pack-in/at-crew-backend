package com.atcrew.artwork.internal.web.dto;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.Visibility;
import com.atcrew.artwork.WorkDuration;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "작품 업로드 요청")
public record UploadArtworkRequest(

        @ArraySchema(arraySchema = @Schema(
                description = "R2 업로드를 마친 이미지 key 목록 (1~20개). Presigned URL 발급 응답의 key를 순서대로 넣습니다",
                example = "[\"raw/019ff383-2c63-765b-af15-8a2c870a29ec.jpg\"]"))
        @NotEmpty @Size(max = 20)
        List<String> imageKeys, // 이미지 key 목록

        @Schema(description = "대표 이미지 인덱스 (imageKeys 기준, 0부터 시작). imageKeys 길이 범위를 벗어나면 "
                + "INVALID_REPRESENTATIVE_INDEX(400)", example = "0")
        @Min(0)
        int representativeImageIndex, // 대표 이미지 인덱스

        @Schema(description = "작가가 직접 지정할 썸네일 R2 key (선택). 미지정 시 대표 이미지의 처리 결과 썸네일이 사용됩니다",
                example = "raw/019ff383-2c63-765b-af15-8a2c870a29ec.jpg", nullable = true)
        String thumbnailKey, // 직접 지정 썸네일 key

        @Schema(description = "이미지 배치 방식 (VERTICAL_SCROLL: 세로 스크롤, HORIZONTAL_SWIPE: 가로 스와이프)",
                example = "VERTICAL_SCROLL")
        @NotNull
        ImageLayoutType imageLayoutType, // 이미지 배치 방식

        @Schema(description = "작품 제목 (최대 100자)", example = "겨울 숲의 아이")
        @NotBlank @Size(max = 100)
        String title, // 작품 제목

        @Schema(description = "작품 설명 (선택, 최대 500자)", example = "겨울 숲을 배경으로 그린 일러스트입니다.", nullable = true)
        @Size(max = 500)
        String description, // 작품 설명

        @Schema(description = "작품 분야 (ILLUSTRATION: 일러스트, WEBTOON: 웹툰, PRINT_COMIC: 출판만화, "
                + "ANIMATION: 애니메이션, ETC: 기타)", example = "ILLUSTRATION")
        @NotNull
        ArtworkField artworkField, // 작품 분야

        @Schema(description = "창작 유형 (ORIGINAL: 1차 창작, SECONDARY: 2차 창작, FAN_ART: 팬아트, "
                + "OC: 오리지널 캐릭터, COMMISSION: 커미션)", example = "ORIGINAL")
        @NotNull
        CreativeType creativeType, // 창작 유형

        @ArraySchema(arraySchema = @Schema(
                description = "참여 역할 목록 (1개 이상, TOTAL_ARTWORK: 총작화, ADAPTATION_STORYBOARD: 각색콘티, "
                        + "STORYBOARD: 콘티, DIRECTION: 연출, LINEART: 선화, SKETCH: 스케치, COLORING: 채색, BASE_COLOR: 밑색, "
                        + "TONE_WORK: 1도명암, POST_PROCESSING: 후보정, FULL_COLOR: 풀채색, PANEL_DECORATION: 컷꾸미기, "
                        + "THREE_D_MODELING: 3D모델링, MATERIAL_MAKING: 소재제작, MATERIAL_PLACEMENT: 소재배치, BACKGROUND: 배경, "
                        + "WEBNOVEL_COVER: 웹소설표지, CHARACTER_DESIGN: 캐릭터디자인, CHARACTER_SHEET: 캐릭터시트, "
                        + "TYPOGRAPHY: 타이포, BROADCAST_THUMBNAIL: 방송썸네일, ETC: 직접입력)",
                example = "[\"TOTAL_ARTWORK\", \"COLORING\"]"))
        @NotEmpty
        List<ArtworkRole> roles, // 참여 역할 목록

        @ArraySchema(arraySchema = @Schema(description = "장르 목록 (선택). 중복은 제거됩니다",
                example = "[\"판타지\"]", nullable = true))
        List<String> genres, // 장르 목록

        @ArraySchema(arraySchema = @Schema(description = "태그 목록 (선택, 최대 7개). 중복은 제거됩니다",
                example = "[\"겨울\", \"판타지\"]", nullable = true))
        @Size(max = 7)
        List<String> tags, // 태그 목록

        @Schema(description = "연령 등급 (ALL: 전체연령가, R18: 성인물-성적 콘텐츠, G18: 성인물-고어/폭력)", example = "ALL")
        @NotNull
        AgeRating ageRating, // 연령 등급

        @Schema(description = "공개 범위 (PUBLIC: 전체 공개, LINK_ONLY: 링크 공개, PRIVATE: 비공개). "
                + "업로드 시점에는 자유롭게 지정할 수 있고, 이후 변경은 공개 상태 변경 API를 사용합니다", example = "PUBLIC")
        @NotNull
        Visibility visibility, // 공개 범위

        @ArraySchema(arraySchema = @Schema(description = "사용 툴 목록 (선택). 중복은 제거됩니다",
                example = "[\"Photoshop\", \"Clip Studio Paint\"]", nullable = true))
        List<String> tools, // 사용 툴 목록

        @Schema(description = "작업 소요 시간 (선택)", nullable = true)
        WorkDuration workDuration, // 작업 소요 시간

        @Schema(description = "컷 수 (선택)", example = "12", nullable = true)
        Integer cutCount, // 컷 수

        @ArraySchema(arraySchema = @Schema(description = "영상 링크 목록 (선택, 최대 5개)",
                example = "[\"https://youtu.be/abcdefg\"]", nullable = true))
        @Size(max = 5)
        List<String> videoLinks, // 영상 링크 목록

        @ArraySchema(arraySchema = @Schema(description = "사용 소재 목록 (선택)", nullable = true))
        @Valid
        List<MaterialRequest> materials // 사용 소재 목록
) {
}
