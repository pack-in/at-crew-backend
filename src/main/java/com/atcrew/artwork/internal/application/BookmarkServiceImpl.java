package com.atcrew.artwork.internal.application;

import com.atcrew.artwork.ArtworkAccess;
import com.atcrew.artwork.ArtworkStatus;
import com.atcrew.artwork.BookmarkEntryInfo;
import com.atcrew.artwork.BookmarkFolderInfo;
import com.atcrew.artwork.BookmarkService;
import com.atcrew.artwork.internal.domain.artwork.Artwork;
import com.atcrew.artwork.internal.domain.bookmark.BookmarkEntry;
import com.atcrew.artwork.internal.domain.bookmark.BookmarkFolder;
import com.atcrew.artwork.internal.exception.ArtworkErrorCode;
import com.atcrew.artwork.internal.exception.ArtworkException;
import com.atcrew.artwork.internal.persistence.ArtworkRepository;
import com.atcrew.artwork.internal.persistence.BookmarkEntryRepository;
import com.atcrew.artwork.internal.persistence.BookmarkFolderRepository;
import com.atcrew.common.response.CursorPage;
import com.atcrew.media.MediaService;
import com.atcrew.member.MemberInfo;
import com.atcrew.member.MemberService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class BookmarkServiceImpl implements BookmarkService {

    // 저장 시점 이후 작품이 삭제·차단되면 필터로 빠지는데, 단순히 상위 size+1건만 보고 거르면 그 행들이
    // 앞쪽(최근 저장 순)에 몰려 있을 때 뒤에 열람 가능한 북마크가 남아 있어도 결과가 비어버린다
    // (portfolio 모듈의 같은 버그, PortfolioServiceImpl.scanViewableArtworks 참고). 청크를 이어서 읽어
    // 필터 통과분이 목표 개수가 될 때까지 채우되, 대량으로 삭제된 북마크가 쌓인 요청의 비용을 무한정
    // 키우지 않도록 상한을 둔다.
    private static final int MAX_SCAN_CHUNKS = 10;

    private final BookmarkFolderRepository folderRepository;
    private final BookmarkEntryRepository entryRepository;
    private final ArtworkRepository artworkRepository;
    private final MemberService memberService;
    private final MediaService mediaService;

    BookmarkServiceImpl(BookmarkFolderRepository folderRepository,
                        BookmarkEntryRepository entryRepository,
                        ArtworkRepository artworkRepository,
                        MemberService memberService,
                        MediaService mediaService) {
        this.folderRepository = folderRepository;
        this.entryRepository = entryRepository;
        this.artworkRepository = artworkRepository;
        this.memberService = memberService;
        this.mediaService = mediaService;
    }

    @Override
    public List<BookmarkFolderInfo> getFolders(String memberId) {
        return folderRepository.findByMemberIdOrderBySortOrderAsc(memberId)
                .stream()
                .map(ArtworkMapper::toFolderInfo)
                .toList();
    }

    @Override
    @Transactional
    public BookmarkFolderInfo createFolder(String memberId, String name) {
        String trimmed = validateFolderName(name);
        if (folderRepository.existsByMemberIdAndName(memberId, trimmed)) {
            throw new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_DUPLICATE_NAME, trimmed);
        }
        int sortOrder = folderRepository.countByMemberId(memberId);
        BookmarkFolder folder = BookmarkFolder.create(memberId, trimmed, sortOrder);
        return ArtworkMapper.toFolderInfo(folderRepository.save(folder));
    }

    @Override
    @Transactional
    public BookmarkFolderInfo renameFolder(String memberId, String folderId, String name) {
        BookmarkFolder folder = folderRepository.findByIdAndMemberId(folderId, memberId)
                .orElseThrow(() -> new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_NOT_FOUND, folderId));
        folder.assertOwner(memberId);
        String trimmed = validateFolderName(name);
        if (folderRepository.existsByMemberIdAndNameAndIdNot(memberId, trimmed, folderId)) {
            throw new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_DUPLICATE_NAME, trimmed);
        }
        folder.rename(trimmed);
        return ArtworkMapper.toFolderInfo(folder);
    }

    // 공백 불가·최대 20자 제한은 생성·이름 변경에 공통이다.
    private String validateFolderName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isBlank()) {
            throw new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_NAME_BLANK);
        }
        if (trimmed.length() > 20) {
            throw new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_NAME_BLANK, "폴더명은 최대 20자입니다");
        }
        return trimmed;
    }

    @Override
    @Transactional
    public void deleteFolder(String memberId, String folderId) {
        BookmarkFolder folder = folderRepository.findByIdAndMemberId(folderId, memberId)
                .orElseThrow(() -> new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_NOT_FOUND, folderId));
        folder.assertOwner(memberId);
        // 폴더에 속한 북마크는 기본 폴더(null)로 이동 — 설계: DB 데이터 보존
        List<BookmarkEntry> entries = entryRepository.findByMemberIdAndFolderIdOrderBySavedAtDesc(memberId, folderId);
        entries.forEach(e -> e.moveToFolder(null));
        entryRepository.saveAll(entries);
        folderRepository.delete(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<BookmarkEntryInfo> getBookmarks(String memberId, String folderId,
                                                       String cursor, int size) {
        int target = size + 1;
        BookmarkCursor scanFrom = cursor != null ? parseCursor(cursor) : null;

        // 노출 기준은 저장 기준(saveBookmark)과 같아야 한다 — visibility == PUBLIC만 보여주면 포트폴리오
        // 한정 공개 작품처럼 저장은 되는데 목록에는 영원히 안 보이는 북마크가 생긴다.
        // 운영 차단 작품은 본인 작품이라도 목록에서 뺀다(마이페이지_작가-R39) — accessFor가 작성자
        // 본인에게는 차단 작품도 허용하므로 여기서 따로 제외한다.
        //
        // 필터 통과분이 target개가 될 때까지 저장 시각 내림차순으로 다음 청크를 이어서 읽는다 — hasNext
        // 판정도 필터 이후 개수 기준이어야 "items는 비었는데 hasNext=true"가 나오지 않는다. 청크 경계가
        // 여러 번 생기는 만큼(§BookmarkCursor) 동률 처리가 중요해, 전체를 한 트랜잭션으로 묶어 청크마다
        // 다른 스냅샷을 보지 않게 한다.
        List<BookmarkEntry> matchedEntries = new ArrayList<>();
        Map<String, Artwork> artworkMap = new HashMap<>();
        BookmarkEntry lastScannedEntry = null;
        int scannedChunks = 0;
        boolean exhausted = false;

        while (matchedEntries.size() < target && scannedChunks < MAX_SCAN_CHUNKS) {
            List<BookmarkEntry> chunk = fetchBookmarkChunk(memberId, folderId, scanFrom, target);
            if (chunk.isEmpty()) {
                exhausted = true;
                break;
            }
            scannedChunks++;

            Map<String, Artwork> chunkArtworks = artworkRepository
                    .findAllById(chunk.stream().map(BookmarkEntry::getArtworkId).toList())
                    .stream()
                    .filter(a -> a.getStatus() == ArtworkStatus.READY
                            && !a.isBlocked()
                            && a.accessFor(memberId) == ArtworkAccess.ALLOWED)
                    .collect(Collectors.toMap(Artwork::getId, a -> a));

            for (BookmarkEntry entry : chunk) {
                if (matchedEntries.size() == target) {
                    break;
                }
                lastScannedEntry = entry;
                Artwork artwork = chunkArtworks.get(entry.getArtworkId());
                if (artwork != null) {
                    matchedEntries.add(entry);
                    artworkMap.put(entry.getArtworkId(), artwork);
                }
            }
            // 다음 청크는 이번에 읽은 마지막 행 다음부터 — savedAt이 내림차순이라 루프가 반드시 끝난다.
            BookmarkEntry lastInChunk = chunk.get(chunk.size() - 1);
            scanFrom = new BookmarkCursor(lastInChunk.getSavedAt(), lastInChunk.getId());
        }

        boolean hasNext = matchedEntries.size() > size;
        List<BookmarkEntry> page = hasNext ? matchedEntries.subList(0, size) : matchedEntries;

        Set<String> authorIds = page.stream()
                .map(e -> artworkMap.get(e.getArtworkId()).getAuthorId())
                .collect(Collectors.toSet());
        // 배치 조회 — 자세한 배경은 ArtworkServiceImpl의 같은 지점 주석 참고(이슈 #112).
        Map<String, MemberInfo> authorMap = memberService.findAllByIds(authorIds);

        // 이미지는 media가 갖는다(#193) — 목록은 한 번에 읽는다.
        Set<String> pageArtworkIds = page.stream().map(BookmarkEntry::getArtworkId).collect(Collectors.toSet());
        Map<String, ArtworkMedia> mediaByArtwork = ArtworkMedia.loadAll(mediaService, pageArtworkIds);
        List<BookmarkEntryInfo> items = page.stream()
                .map(e -> {
                    Artwork artwork = artworkMap.get(e.getArtworkId());
                    return ArtworkMapper.toEntryInfo(e, ArtworkMapper.toSummaryInfo(artwork,
                            authorMap.get(artwork.getAuthorId()),
                            mediaByArtwork.get(artwork.getId())));
                })
                .toList();

        // size=0인데 조건에 맞는 데이터가 있으면 hasNext=true여도 page가 비어 page.get(-1)을 호출하게
        // 된다(이슈 #195) — 그 경우엔 커서를 안전하게 null로 둔다. hasNext가 거짓일 때 lastScannedEntry가
        // null인 경우는 exhausted가 이미 참인 경우뿐이라 따로 가를 필요가 없다.
        if (hasNext && !page.isEmpty()) {
            return CursorPage.of(items, formatCursor(page.get(page.size() - 1)));
        }
        if (hasNext || exhausted) {
            return CursorPage.of(items, null);
        }
        // 스캔 상한에 걸려 중단한 경우 — 마지막으로 읽은 행까지의 커서를 줘 다음 요청이 이어받게 한다.
        return CursorPage.of(items, formatCursor(lastScannedEntry));
    }

    private List<BookmarkEntry> fetchBookmarkChunk(String memberId, String folderId, BookmarkCursor scanFrom,
                                                      int limit) {
        PageRequest pageable = PageRequest.of(0, limit);
        if (folderId != null) {
            return scanFrom != null
                    ? entryRepository.findByMemberIdAndFolderIdBeforeCursor(
                            memberId, folderId, scanFrom.savedAt(), scanFrom.id(), pageable)
                    : entryRepository.findByMemberIdAndFolderIdOrderBySavedAtDescIdDesc(
                            memberId, folderId, pageable);
        }
        return scanFrom != null
                ? entryRepository.findByMemberIdAndFolderIdIsNullBeforeCursor(
                        memberId, scanFrom.savedAt(), scanFrom.id(), pageable)
                : entryRepository.findByMemberIdAndFolderIdIsNullOrderBySavedAtDescIdDesc(memberId, pageable);
    }

    @Override
    @Transactional
    public BookmarkEntryInfo saveBookmark(String memberId, String artworkId, String folderId) {
        Artwork artwork = artworkRepository.findById(artworkId)
                .orElseThrow(() -> new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_FOUND, artworkId));
        // 북마크는 GONE/PRIVATE를 구분해 보여줄 필요가 없어 접근 불가 사유를 묶어 404로 응답한다.
        // 상태 조건은 기존 isVisibleTo와 동일하게 유지한다 — 본인 작품이라도 처리 중·휴지통 작품은 북마크 대상이 아니다.
        if (artwork.getStatus() != ArtworkStatus.READY
                || artwork.accessFor(memberId) != ArtworkAccess.ALLOWED) {
            throw new ArtworkException(ArtworkErrorCode.ARTWORK_NOT_FOUND, artworkId);
        }

        if (folderId != null) {
            folderRepository.findByIdAndMemberId(folderId, memberId)
                    .orElseThrow(() -> new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_NOT_FOUND, folderId));
        }
        if (entryRepository.existsByMemberIdAndArtworkId(memberId, artworkId)) {
            throw new ArtworkException(ArtworkErrorCode.BOOKMARK_ALREADY_EXISTS, artworkId);
        }
        BookmarkEntry entry = BookmarkEntry.create(memberId, artworkId, folderId, artwork.getVisibility());
        BookmarkEntry saved = entryRepository.save(entry);
        // 북마크순 정렬용 집계(이슈 #78) — 중복 저장은 위에서 이미 막았으므로 여기서는 항상 1 증가한다.
        artworkRepository.incrementBookmarkCount(artworkId);
        MemberInfo author = memberService.findById(artwork.getAuthorId());
        return ArtworkMapper.toEntryInfo(saved, ArtworkMapper.toSummaryInfo(artwork, author,
                ArtworkMedia.load(mediaService, artwork.getId())));
    }

    @Override
    @Transactional
    public void removeBookmark(String memberId, String artworkId) {
        BookmarkEntry entry = entryRepository.findByMemberIdAndArtworkId(memberId, artworkId)
                .orElseThrow(() -> new ArtworkException(ArtworkErrorCode.BOOKMARK_NOT_FOUND, artworkId));
        entryRepository.delete(entry);
        // 작품이 이미 영구 삭제됐으면 갱신 대상이 없어 0행이 바뀐다 — 북마크 해제 자체는 성공시킨다.
        artworkRepository.decrementBookmarkCount(artworkId);
    }

    @Override
    @Transactional
    public void moveBookmarks(String memberId, List<String> artworkIds, String targetFolderId) {
        if (targetFolderId != null) {
            folderRepository.findByIdAndMemberId(targetFolderId, memberId)
                    .orElseThrow(() -> new ArtworkException(ArtworkErrorCode.BOOKMARK_FOLDER_NOT_FOUND, targetFolderId));
        }
        List<BookmarkEntry> entries = entryRepository.findByMemberIdAndArtworkIdIn(memberId, artworkIds);
        entries.forEach(e -> e.moveToFolder(targetFolderId));
        entryRepository.saveAll(entries);
    }

    /**
     * 목록 커서 — 저장 시각과 동률을 가르는 id 쌍이다(PortfolioServiceImpl.PortfolioCursor와 동일한 이유).
     * {@code bookmark_entries.saved_at}은 DATETIME(6)이라 마이크로초까지 저장되는데, 커서를 밀리초로
     * 자르면 같은 밀리초의 뒷부분 행이 통째로 건너뛰어진다. 그래서 마이크로초 해상도로 인코딩하고
     * 동률은 id로 가른다.
     */
    private record BookmarkCursor(Instant savedAt, String id) {
    }

    // 커서 문자열은 "<epochMicros>_<id>"다 — id(UUID)에는 '_'가 없어 첫 구분자로 안전하게 나뉜다.
    private String formatCursor(BookmarkEntry entry) {
        Instant value = entry.getSavedAt();
        long micros = value.getEpochSecond() * 1_000_000 + value.getNano() / 1_000;
        return micros + "_" + entry.getId();
    }

    private BookmarkCursor parseCursor(String cursor) {
        int separator = cursor.indexOf('_');
        if (separator <= 0 || separator == cursor.length() - 1) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_CURSOR);
        }
        try {
            long micros = Long.parseLong(cursor.substring(0, separator));
            return new BookmarkCursor(
                    Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000),
                            Math.floorMod(micros, 1_000_000) * 1_000L),
                    cursor.substring(separator + 1));
        } catch (NumberFormatException e) {
            throw new ArtworkException(ArtworkErrorCode.INVALID_CURSOR);
        }
    }
}
