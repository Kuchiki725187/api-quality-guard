package com.quality.guard.module.testexec.definition;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.PageResult;
import com.quality.guard.module.testexec.definition.dto.ApiDefinitionCreateDTO;
import com.quality.guard.module.testexec.definition.dto.ApiDefinitionUpdateDTO;
import com.quality.guard.module.testexec.definition.service.ApiDefinitionService;
import com.quality.guard.module.testexec.definition.vo.ApiDefinitionVO;
import com.quality.guard.module.testexec.definition.vo.ApiImportResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ApiDefinitionServiceTest {

    @Autowired
    private ApiDefinitionService apiDefinitionService;

    @Test
    void crudFlow() {
        ApiDefinitionCreateDTO create = new ApiDefinitionCreateDTO();
        create.setName("查询商品详情");
        create.setHttpMethod("get");
        create.setPath("/products/{id}");
        create.setBaseUrl("http://localhost:9090");

        Long id = apiDefinitionService.create(create);
        assertNotNull(id);

        ApiDefinitionVO detail = apiDefinitionService.detail(id);
        assertEquals("GET", detail.getHttpMethod());
        assertEquals("查询商品详情", detail.getName());
        assertEquals("http://localhost:9090", detail.getBaseUrl());

        ApiDefinitionUpdateDTO update = new ApiDefinitionUpdateDTO();
        update.setName("查询商品详情V2");
        update.setDescription("更新后的描述");
        apiDefinitionService.update(id, update);

        ApiDefinitionVO updated = apiDefinitionService.detail(id);
        assertEquals("查询商品详情V2", updated.getName());
        assertEquals("更新后的描述", updated.getDescription());
        assertEquals("GET", updated.getHttpMethod());

        PageResult<ApiDefinitionVO> page = apiDefinitionService.page(1, 10, "查询商品详情V2", null);
        assertEquals(1, page.getTotal());
        assertEquals(id, page.getRecords().get(0).getId());

        apiDefinitionService.delete(id);
        assertThrows(BusinessException.class, () -> apiDefinitionService.detail(id));
    }

    @Test
    void detailNotFoundThrowsBusinessException() {
        BusinessException e = assertThrows(BusinessException.class, () -> apiDefinitionService.detail(999999L));
        assertEquals(404, e.getCode());
    }

    @Test
    void importDocumentPersistsParsedApis() throws IOException {
        cleanupUserApis();
        byte[] content;
        try (InputStream in = getClass().getResourceAsStream("/openapi/openapi-v3-sample.json")) {
            content = in.readAllBytes();
        }
        MockMultipartFile file = new MockMultipartFile("file", "openapi-v3-sample.json", "application/json", content);

        ApiImportResultVO result = apiDefinitionService.importDocument(file);

        assertEquals("用户服务 API", result.getDocumentTitle());
        assertEquals(4, result.getTotalParsed());
        assertEquals(4, result.getImported());
        assertTrue(result.getFailures().isEmpty());

        PageResult<ApiDefinitionVO> page = apiDefinitionService.page(1, 10, null, null);
        assertTrue(page.getTotal() >= 4);

        ApiDefinitionVO getUser = page.getRecords().stream()
                .filter(v -> "getUserById".equals(v.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("http://localhost:9090", getUser.getBaseUrl());
        assertNotNull(getUser.getRequestSchema());
        assertEquals(1, getUser.getRequestSchema().getParams().size());
    }

    @Test
    void importTwiceDeduplicates() throws IOException {
        cleanupUserApis();
        byte[] content;
        try (InputStream in = getClass().getResourceAsStream("/openapi/openapi-v3-sample.json")) {
            content = in.readAllBytes();
        }
        MockMultipartFile file = new MockMultipartFile("file", "openapi-v3-sample.json", "application/json", content);

        ApiImportResultVO first = apiDefinitionService.importDocument(file);
        assertEquals(4, first.getImported());
        assertEquals(0, first.getUpdated());

        ApiImportResultVO second = apiDefinitionService.importDocument(file);
        assertEquals(0, second.getImported());
        assertEquals(4, second.getUpdated());

        PageResult<ApiDefinitionVO> page = apiDefinitionService.page(1, 100, null, null);
        long matched = page.getRecords().stream()
                .filter(v -> v.getPath().startsWith("/users"))
                .count();
        assertEquals(4, matched);
    }

    /**
     * 逻辑删除库中所有 /users 前缀的接口，保证导入类测试的"新增"断言不受
     * 已有数据影响；测试事务回滚后数据恢复
     */
    private void cleanupUserApis() {
        PageResult<ApiDefinitionVO> existing = apiDefinitionService.page(1, 200, null, null);
        existing.getRecords().stream()
                .filter(v -> v.getPath().startsWith("/users"))
                .forEach(v -> apiDefinitionService.delete(v.getId()));
    }

    @Test
    void importUnsupportedFileTypeThrows() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "hello".getBytes());
        BusinessException e = assertThrows(BusinessException.class, () -> apiDefinitionService.importDocument(file));
        assertEquals(400, e.getCode());
    }
}
