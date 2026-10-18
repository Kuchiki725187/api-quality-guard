package com.quality.guard.module.testexec.model;

import java.net.http.HttpRequest;
import java.util.Map;

/**
 * HttpRequestBuilder 的构建产物：JDK 请求对象 + 快照所需的元信息。
 *
 * @param request  可直接发送的 JDK HttpRequest
 * @param url      最终完整 URL（含 query，已用于快照）
 * @param headers  实际使用的请求头（用于快照）
 * @param bodyText 请求体 JSON 文本；无体时为 null
 */
public record BuiltRequest(HttpRequest request, String url, Map<String, String> headers, String bodyText) {
}
