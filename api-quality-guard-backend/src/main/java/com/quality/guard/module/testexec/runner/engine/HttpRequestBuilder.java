package com.quality.guard.module.testexec.runner.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.model.BuiltRequest;
import com.quality.guard.module.testexec.model.RequestParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP 请求构建器（执行引擎的第一环）：
 * 把「接口定义 + 用例参数值」组装成可发送的 JDK HttpRequest，并保留快照所需的元信息。
 */
@Component
@RequiredArgsConstructor
public class HttpRequestBuilder {

    /** 单请求默认超时（毫秒） */
    private static final long DEFAULT_TIMEOUT_MS = 5000;

    public static final String JSON_CONTENT_TYPE = "application/json";

    private final ObjectMapper objectMapper;

    /**
     * 构建请求。
     *
     * @param apiDefinition   接口定义（httpMethod / path / baseUrl）
     * @param requestParams   用例参数值（path / query / headers / body 分组）
     * @param baseUrlOverride 请求级地址覆盖；为空则使用 apiDefinition.baseUrl
     * @return 构建产物（HttpRequest + 最终 URL + 请求头 + body 文本）
     */
    public BuiltRequest build(ApiDefinition apiDefinition, RequestParams requestParams, String baseUrlOverride) {
        // 1. 确定 baseUrl：请求级覆盖 > 接口定义配置；都没有则无法执行（可预期错误）
        String baseUrl = StringUtils.hasText(baseUrlOverride) ? baseUrlOverride : apiDefinition.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未配置被测服务地址(baseUrl)");
        }

        // 2. 拼接最终 URL（path 模板替换 + query 参数，值全部编码）
        String url = buildUrl(baseUrl, apiDefinition.getPath(), requestParams);

        // 3. 请求头：默认 Accept，再合并用例自定义头（同名覆盖）
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", JSON_CONTENT_TYPE);
        if (requestParams.getHeaders() != null) {
            requestParams.getHeaders().forEach((name, value) -> headers.put(name, String.valueOf(value)));
        }

        // 4. 请求体：body 非空则 JSON 序列化；用例未显式提供 Content-Type 时补齐
        String bodyText = null;
        if (requestParams.getBody() != null) {
            try {
                //将body转换为字符串
                bodyText = objectMapper.writeValueAsString(requestParams.getBody());
            } catch (JsonProcessingException e) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "请求体序列化失败: " + e.getOriginalMessage());
            }
            headers.putIfAbsent("Content-Type", JSON_CONTENT_TYPE);
        }

        // 5. 组装 HttpRequest：统一走 method(name, publisher)，兼容 POST 无体 / DELETE 带体等组合
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(DEFAULT_TIMEOUT_MS));
        //方法引用(key, value) -> builder.header(key, value)
        headers.forEach(builder::header);
        //构建请求体
        HttpRequest.BodyPublisher publisher = bodyText == null
                ? HttpRequest.BodyPublishers.noBody()
                //提供字符串形式的body
                : HttpRequest.BodyPublishers.ofString(bodyText, StandardCharsets.UTF_8);
        HttpRequest request = builder.method(apiDefinition.getHttpMethod().toUpperCase(), publisher).build();

        // 6. 返回构建产物（url/headers/bodyText 供快照使用）
        return new BuiltRequest(request, url, headers, bodyText);
    }

    /** 拼接最终 URL：baseUrl 去尾斜杠 + path 模板替换 + query 参数 */
    private String buildUrl(String baseUrl, String path, RequestParams requestParams) {
        //http://localhost:8080/users/123?page=2&size=10一个完整的示例
        //如果url末尾有/就去掉，因为path路径开头也可能有/
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String finalPath = path == null ? "" : path;

        // path 模板替换：/users/{id} + {"id": 1} → /users/1
        if (requestParams.getPath() != null) {
            for (Map.Entry<String, Object> entry : requestParams.getPath().entrySet()) {
                //将形如{id}改为实际值，例如/users/{id}->/users/123
                finalPath = finalPath.replace("{" + entry.getKey() + "}",
                        encode(String.valueOf(entry.getValue())));//利用encode编码防止参数中的特殊字符破坏URL结构
            }
        }
        //确保path以/开头
        if (!finalPath.startsWith("/")) {
            finalPath = "/" + finalPath;
        }

        StringBuilder url = new StringBuilder(base).append(finalPath);
        if (requestParams.getQuery() != null && !requestParams.getQuery().isEmpty()) {
            String query = requestParams.getQuery().entrySet().stream()
                    //将entrySet中的每一个key=value转换为字符串形式
                    .map(e -> encode(e.getKey()) + "=" + encode(String.valueOf(e.getValue())))
                    .collect(Collectors.joining("&"));
            url.append("?").append(query);
        }
        return url.toString();
    }

    /**
     * URL 编码：URLEncoder 会把空格编成 "+"（表单语义），
     * 在 URL 路径/查询里统一替换为 "%20"
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
