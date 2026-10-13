package com.quality.guard.infrastructure.openapi;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一份文档的完整解析结果
 */
@Data
public class ParsedApiDocument {

    private String documentTitle;

    private String documentVersion;

    private List<ParsedApi> apis = new ArrayList<>();
}
