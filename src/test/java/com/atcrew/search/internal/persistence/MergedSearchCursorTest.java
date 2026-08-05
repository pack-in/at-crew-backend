package com.atcrew.search.internal.persistence;

import com.atcrew.search.internal.exception.SearchException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MergedSearchCursorTest {

    @Test
    void 인코딩_후_디코딩하면_소스별_서브커서가_그대로_복원된다() {
        String artworkCursor = SearchCursor.encode(List.of(1753900800000L, "artwork-1"));
        String recruitCursor = SearchCursor.encode(List.of(1753900800000L, "recruit-1"));

        String cursor = MergedSearchCursor.encode(new MergedSearchCursor.SubCursors(artworkCursor, recruitCursor));
        MergedSearchCursor.SubCursors decoded = MergedSearchCursor.decode(cursor);

        assertThat(decoded.artworkCursor()).isEqualTo(artworkCursor);
        assertThat(decoded.recruitCursor()).isEqualTo(recruitCursor);
    }

    @Test
    void 한쪽_소스의_서브커서가_null이어도_왕복된다() {
        String artworkCursor = SearchCursor.encode(List.of(1753900800000L, "artwork-1"));

        String cursor = MergedSearchCursor.encode(new MergedSearchCursor.SubCursors(artworkCursor, null));
        MergedSearchCursor.SubCursors decoded = MergedSearchCursor.decode(cursor);

        assertThat(decoded.artworkCursor()).isEqualTo(artworkCursor);
        assertThat(decoded.recruitCursor()).isNull();
    }

    @Test
    void 잘못된_base64_문자열이면_INVALID_CURSOR_예외() {
        assertThatThrownBy(() -> MergedSearchCursor.decode("not a valid base64!!"))
                .isInstanceOf(SearchException.class);
    }
}
