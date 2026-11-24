
package org.courseflow.catalog.dto;

import org.courseflow.catalog.entity.Tag;

public record TagResponse(
        Long id,
        long version,
        String name
) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getVersion(),
                tag.getName()
        );
    }
}
