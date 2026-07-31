package com.atcrew.search.internal.persistence;

import com.atcrew.search.internal.exception.SearchException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCursorTest {

    @Test
    void 인코딩_후_디코딩하면_원래_값으로_복원된다() {
        List<Object> sortValues = List.of(1753900800000L, "artwork-123");

        String cursor = SearchCursor.encode(sortValues);
        List<String> decoded = SearchCursor.decode(cursor);

        assertThat(decoded).containsExactly("1753900800000", "artwork-123");
    }

    @Test
    void 관련도_정렬용_score_포함_커서도_왕복된다() {
        List<Object> sortValues = List.of(3.14, 1753900800000L, "artwork-456");

        String cursor = SearchCursor.encode(sortValues);
        List<String> decoded = SearchCursor.decode(cursor);

        assertThat(decoded).containsExactly("3.14", "1753900800000", "artwork-456");
    }

    @Test
    void 잘못된_base64_문자열이면_INVALID_CURSOR_예외() {
        assertThatThrownBy(() -> SearchCursor.decode("not a valid base64!!"))
                .isInstanceOf(SearchException.class);
    }
}
