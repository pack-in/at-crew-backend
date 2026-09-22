package com.atcrew.media;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MediaService {
    /**
     * @param fileSizes 업로드할 원본의 바이트 크기. 클라이언트가 보내지 않으면 null이며 이때 크기 검사는
     *                  건너뛴다 — 신고값을 믿는 사전 검사라 Worker의 실측 검사를 대체하지 않는다.
     */
    List<PresignedUrlInfo> generatePresignedUrls(String memberId, int count, List<String> contentTypes,
                                                 List<Long> fileSizes);

    /**
     * {@code keys} 중 이 회원에게 발급되지 않은 key — 비어 있지 않으면 호출자가 요청을 거부한다(#190).
     *
     * <p>업로드 key에는 발급 시점에 소유자 서명이 들어간다. 조회 없이 서명만 다시 계산해 판정하므로 호출 비용이
     * 없다. Worker가 만든 변형본(`thumb/…`)이나 옛 형식 key에는 서명이 없어 함께 걸린다.
     */
    Set<String> unownedKeys(String memberId, Collection<String> keys);
    void registerAndTriggerProcessing(MediaOwnerType ownerType, String ownerId, List<String> imageKeys,
                                      MediaVariantProfile variantProfile, MediaQualityTier qualityTier);
    /**
     * 소유자의 이미지 목록을 {@code desired}와 같게 맞추고, 맞춘 결과를 순서대로 돌려준다. 이미지 상태·변형본 key의
     * 단일 원천이 media이므로 유지·추가·삭제 판정도 여기서만 한다 — 소유자는 돌려받은 결과만 반영한다(#193).
     *
     * <p>판정 규칙:
     * <ul>
     *   <li>남는 key가 DONE이면 행과 변환 결과를 그대로 두고 <b>다시 트리거하지 않는다</b>. Worker는 DONE 콜백이
     *       도달한 뒤 raw를 지우므로, 재트리거하면 "원본 없음"으로 FAILED가 된다.</li>
     *   <li>남는 key가 PENDING·FAILED면 raw가 아직 있으므로 다시 트리거한다.</li>
     *   <li>빠진 key만 고아 큐로 보내고 행을 지운다.</li>
     *   <li>새 key는 PENDING으로 등록하고 트리거한다.</li>
     * </ul>
     *
     * <p>{@code variantProfile}·{@code qualityTier}는 <b>새로 등록하는 행에만</b> 쓴다. 기존 행은 업로드 시점에
     * 확정된 값을 유지한다(요금제 변경으로 이미 올라간 이미지의 화질이 바뀌지 않게).
     *
     * @param desired 원하는 목록. 순서가 곧 ordinal이고, 빈 목록이면 소유자의 자산을 전부 지운다.
     */
    List<MediaAssetInfo> syncAssets(MediaOwnerType ownerType, String ownerId, List<MediaAssetSpec> desired,
                                    MediaVariantProfile variantProfile, MediaQualityTier qualityTier);
    List<MediaAssetInfo> getAssets(MediaOwnerType ownerType, String ownerId);
    /** 목록 화면용 일괄 조회 — 소유자 ID마다 ordinal 순 자산 목록. 자산이 없는 ID는 결과에 담기지 않는다. */
    Map<String, List<MediaAssetInfo>> getAssets(MediaOwnerType ownerType, Collection<String> ownerIds);
    /**
     * 소유자의 media_assets 행을 전부 제거하고, 행이 가리키던 파일 중 {@code handledKeys}에 없는 것을 고아 큐로 넘긴다.
     * {@code handledKeys}는 호출자가 이미 지웠거나 고아 큐에 넣은 key다 — 같은 key를 두 번 적재하지 않기 위해 받는다.
     */
    void deleteAssetsForOwner(MediaOwnerType ownerType, String ownerId, Collection<String> handledKeys);
    void deleteFiles(List<String> keys);
    void markOrphaned(List<String> keys);
}
