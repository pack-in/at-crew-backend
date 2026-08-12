package com.atcrew.artwork.internal.web.dto;

import com.atcrew.artwork.AgeRating;
import com.atcrew.artwork.ArtworkField;
import com.atcrew.artwork.ArtworkRole;
import com.atcrew.artwork.CreativeType;
import com.atcrew.artwork.ImageLayoutType;
import com.atcrew.artwork.WorkDuration;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "작품 수정 요청 — 모든 필드가 선택이며, 보낸 필드만 반영되는 부분 수정입니다. "
        + "값을 null로 보내거나 생략하면 기존 값이 유지됩니다(필드 삭제 불가). "
        + "공개 범위(visibility)는 이 API가 아닌 공개 상태 변경 API로 수정합니다")
public record UpdateArtworkRequest(

        @ArraySchema(arraySchema = @Schema(
                description = "교체할 이미지 key 목록 (1~20개). 보내면 기존 이미지를 전부 교체하고 작품 상태가 다시 "
                        + "PROCESSING으로 돌아가며, 제외된 기존 이미지는 정리 대상이 됩니다",
                example = "[\"raw/019ff384-0362-7158-bb7f-f6fbdd02df60.webp\"]", nullable = true))
        @Size(max = 20)
        List<String> imageKeys, // 교체할 이미지 key 목록

        @Schema(description = "대표 이미지 인덱스 (0부터 시작). imageKeys와 함께 보내면 새 목록 기준, 단독으로 보내면 "
                + "기존 이미지 목록 기준으로 적용됩니다. 범위를 벗어나면 INVALID_REPRESENTATIVE_INDEX(400)",
                example = "0", nullable = true)
        @Min(0)
        Integer representativeImageIndex, // 대표 이미지 인덱스

        @Schema(description = "작가가 직접 지정할 썸네일 R2 key",
                example = "raw/019ff383-2c63-765b-af15-8a2c870a29ec.jpg", nullable = true)
        String thumbnailKey, // 직접 지정 썸네일 key

        @Schema(description = "이미지 배치 방식 (VERTICAL_SCROLL: 세로 스크롤, HORIZONTAL_SWIPE: 가로 스와이프)",
                example = "VERTICAL_SCROLL", nullable = true)
        ImageLayoutType imageLayoutType, // 이미지 배치 방식

        @Schema(description = "작품 제목 (최대 100자)", example = "겨울 숲의 아이 (수정)", nullable = true)
        @Size(max = 100)
        String title, // 작품 제목

        @Schema(description = "작품 설명 (최대 500자)", example = "설명을 수정했습니다.", nullable = true)
        @Size(max = 500)
        String description, // 작품 설명

        @Schema(description = "작품 분야 (ILLUSTRATION: 일러스트, WEBTOON: 웹툰, PRINT_COMIC: 출판만화, "
                + "ANIMATION: 애니메이션, ETC: 기타)", example = "ILLUSTRATION", nullable = true)
        ArtworkField artworkField, // 작품 분야

        @Schema(description = "창작 유형 (ORIGINAL: 1차 창작, SECONDARY: 2차 창작, FAN_ART: 팬아트, "
                + "OC: 오리지널 캐릭터, COMMISSION: 커미션)", example = "ORIGINAL", nullable = true)
        CreativeType creativeType, // 창작 유형

        @ArraySchema(arraySchema = @Schema(
                description = "참여 역할 목록 — 보내면 기존 목록을 통째로 대체합니다 (TOTAL_ARTWORK: 총작화, "
                        + "ADAPTATION_STORYBOARD: 각색콘티, STORYBOARD: 콘티, DIRECTION: 연출, LINEART: 선화, SKETCH: 스케치, "
                        + "COLORING: 채색, BASE_COLOR: 밑색, TONE_WORK: 1도명암, POST_PROCESSING: 후보정, FULL_COLOR: 풀채색, "
                        + "PANEL_DECORATION: 컷꾸미기, THREE_D_MODELING: 3D모델링, MATERIAL_MAKING: 소재제작, "
                        + "MATERIAL_PLACEMENT: 소재배치, BACKGROUND: 배경, WEBNOVEL_COVER: 웹소설표지, "
                        + "CHARACTER_DESIGN: 캐릭터디자인, CHARACTER_SHEET: 캐릭터시트, TYPOGRAPHY: 타이포, "
                        + "BROADCAST_THUMBNAIL: 방송썸네일, ETC: 직접입력)",
                example = "[\"TOTAL_ARTWORK\"]", nullable = true))
        List<ArtworkRole> roles, // 참여 역할 목록

        @ArraySchema(arraySchema = @Schema(description = "장르 목록 — 보내면 기존 목록을 통째로 대체합니다",
                example = "[\"판타지\"]", nullable = true))
        List<String> genres, // 장르 목록

        @ArraySchema(arraySchema = @Schema(description = "태그 목록 (최대 7개) — 보내면 기존 목록을 통째로 대체합니다",
                example = "[\"겨울\"]", nullable = true))
        @Size(max = 7)
        List<String> tags, // 태그 목록

        @Schema(description = "연령 등급 (ALL: 전체연령가, R18: 성인물-성적 콘텐츠, G18: 성인물-고어/폭력)",
                example = "ALL", nullable = true)
        AgeRating ageRating, // 연령 등급

        @ArraySchema(arraySchema = @Schema(description = "사용 툴 목록 — 보내면 기존 목록을 통째로 대체합니다",
                example = "[\"Photoshop\"]", nullable = true))
        List<String> tools, // 사용 툴 목록

        @Schema(description = "작업 소요 시간", nullable = true)
        WorkDuration workDuration, // 작업 소요 시간

        @Schema(description = "컷 수", example = "20", nullable = true)
        Integer cutCount, // 컷 수

        @ArraySchema(arraySchema = @Schema(description = "영상 링크 목록 (최대 5개) — 보내면 기존 목록을 통째로 대체합니다",
                example = "[\"https://youtu.be/abcdefg\"]", nullable = true))
        @Size(max = 5)
        List<String> videoLinks, // 영상 링크 목록

        @ArraySchema(arraySchema = @Schema(
                description = "사용 소재 목록 — 보내면 기존 목록을 통째로 대체합니다 (빈 배열이면 전체 삭제)", nullable = true))
        @Valid
        List<MaterialRequest> materials // 사용 소재 목록
) {
}
