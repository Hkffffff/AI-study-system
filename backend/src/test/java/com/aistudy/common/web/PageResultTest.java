package com.aistudy.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResultTest {

    @Test
    void mapsItemsAndKeepsPagingInfo() {
        var page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 5);

        PageResult<String> result = PageResult.of(page, i -> "#" + i);

        assertThat(result.items()).containsExactly("#1", "#2");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.total()).isEqualTo(5);
    }
}
