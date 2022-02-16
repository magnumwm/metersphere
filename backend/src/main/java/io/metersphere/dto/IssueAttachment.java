package io.metersphere.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IssueAttachment {
    private String filename;
    private String content;
    private String thumbnail;
}
