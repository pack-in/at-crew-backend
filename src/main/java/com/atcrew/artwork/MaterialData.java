package com.atcrew.artwork;

import java.util.List;

public record MaterialData(
        String name,
        List<String> targets,
        List<String> attachmentKeys
) {
}
