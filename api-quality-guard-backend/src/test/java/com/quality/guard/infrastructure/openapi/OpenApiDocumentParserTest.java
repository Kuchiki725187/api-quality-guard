package com.quality.guard.infrastructure.openapi;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.module.testexec.model.ParamDef;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiDocumentParserTest {

    private final OpenApiDocumentParser parser = new OpenApiDocumentParser();

    @Test
    void parseOpenApi3Document() throws IOException {
        ParsedApiDocument document = parser.parse(readResource("/openapi/openapi-v3-sample.json"));

        assertEquals("用户服务 API", document.getDocumentTitle());
        assertEquals("1.2.0", document.getDocumentVersion());
        assertEquals(4, document.getApis().size());

        ParsedApi getUser = findByName(document, "getUserById");
        assertEquals("GET", getUser.getHttpMethod());
        assertEquals("/users/{id}", getUser.getPath());
        assertEquals("http://localhost:9090", getUser.getBaseUrl());
        assertEquals(200, getUser.getResponseSchema().getSuccessStatusCode());

        ParamDef idParam = getUser.getRequestSchema().getParams().get(0);
        assertEquals("id", idParam.getName());
        assertEquals("path", idParam.getIn());
        assertTrue(idParam.isRequired());
        assertEquals(1, ((Number) idParam.getConstraints().get("minimum")).intValue());
        assertEquals(99999, ((Number) idParam.getConstraints().get("maximum")).intValue());

        ParsedApi createUser = findByName(document, "createUser");
        assertEquals("POST", createUser.getHttpMethod());
        assertEquals(201, createUser.getResponseSchema().getSuccessStatusCode());
        assertEquals("application/json", createUser.getRequestSchema().getContentType());

        ParamDef body = createUser.getRequestSchema().getParams().stream()
                .filter(p -> "body".equals(p.getIn()))
                .findFirst()
                .orElseThrow();
        assertTrue(body.isRequired());
        assertNotNull(body.getSchema().get("properties"));
        assertEquals(2, ((java.util.List<?>) body.getSchema().get("required")).size());

        ParsedApi listUsers = document.getApis().stream()
                .filter(a -> "查询用户列表".equals(a.getName()))
                .findFirst()
                .orElseThrow();
        ParamDef keyword = listUsers.getRequestSchema().getParams().stream()
                .filter(p -> "keyword".equals(p.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(50, ((Number) keyword.getConstraints().get("maxLength")).intValue());

        ParsedApi deleteAvatar = findByName(document, "deleteAvatar");
        assertEquals(204, deleteAvatar.getResponseSchema().getSuccessStatusCode());
        assertNull(deleteAvatar.getResponseSchema().getSchema());
    }

    @Test
    void parseSwagger2Document() throws IOException {
        ParsedApiDocument document = parser.parse(readResource("/openapi/swagger-v2-sample.json"));

        assertEquals("订单服务 API", document.getDocumentTitle());
        assertEquals(2, document.getApis().size());

        ParsedApi getOrder = findByName(document, "getOrder");
        assertEquals("GET", getOrder.getHttpMethod());
        assertNotNull(getOrder.getBaseUrl());
        assertTrue(getOrder.getBaseUrl().contains("localhost:9091"));
        assertTrue(getOrder.getBaseUrl().endsWith("/api"));
        // Swagger 2.0 顶层响应 schema 转换为 responseSchema
        assertNotNull(getOrder.getResponseSchema().getSchema());

        ParsedApi createOrder = findByName(document, "createOrder");
        ParamDef body = createOrder.getRequestSchema().getParams().stream()
                .filter(p -> "body".equals(p.getIn()))
                .findFirst()
                .orElseThrow();
        assertTrue(body.isRequired());
        assertNotNull(body.getSchema().get("properties"));
    }

    @Test
    void parseUnsupportedDocumentThrows() {
        assertThrows(BusinessException.class, () -> parser.parse("this is not a document".getBytes()));
    }

    @Test
    void parseDocumentWithoutPathsThrows() {
        String doc = """
                {"openapi":"3.0.0","info":{"title":"空文档","version":"1.0"},"paths":{}}
                """;
        BusinessException e = assertThrows(BusinessException.class, () -> parser.parse(doc.getBytes()));
        assertTrue(e.getMessage().contains("未解析到任何接口"));
    }

    private ParsedApi findByName(ParsedApiDocument document, String name) {
        return document.getApis().stream()
                .filter(a -> name.equals(a.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到接口: " + name));
    }

    private byte[] readResource(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("测试资源不存在: " + path);
            }
            return in.readAllBytes();
        }
    }
}
