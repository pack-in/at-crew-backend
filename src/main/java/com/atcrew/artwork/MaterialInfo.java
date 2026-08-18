package com.atcrew.artwork;

import java.util.List;

public record MaterialInfo(
        String name,
        List<MaterialTarget> targets,
        List<String> attachmentKeys,
        List<String> links
) {
}
