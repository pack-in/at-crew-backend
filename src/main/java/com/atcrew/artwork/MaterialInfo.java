package com.atcrew.artwork;

import java.util.List;

public record MaterialInfo(
        String name,
        List<String> targets,
        List<String> attachmentKeys
) {
}
