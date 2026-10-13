package com.quality.guard.module.testexec.definition.controller;

import com.quality.guard.common.result.PageResult;
import com.quality.guard.common.result.Result;
import com.quality.guard.module.testexec.definition.dto.ApiDefinitionCreateDTO;
import com.quality.guard.module.testexec.definition.dto.ApiDefinitionUpdateDTO;
import com.quality.guard.module.testexec.definition.service.ApiDefinitionService;
import com.quality.guard.module.testexec.definition.vo.ApiDefinitionVO;
import com.quality.guard.module.testexec.definition.vo.ApiImportResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 接口定义管理：手动 CRUD + OpenAPI / Swagger 文档导入
 */
@RestController
@RequestMapping("/api/apis")
@RequiredArgsConstructor
public class ApiDefinitionController {

    private final ApiDefinitionService apiDefinitionService;

    /**
     * 创建接口
     * @param dto ApiDefinitionCreateDTO dto
     * @return Result
     */
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody ApiDefinitionCreateDTO dto) {
        return Result.success(Map.of("id", apiDefinitionService.create(dto)));
    }

    /**
     * 分页查询接口
     * @param page
     * @param pageSize
     * @param name
     * @param httpMethod
     * @return
     */
    @GetMapping
    public Result<PageResult<ApiDefinitionVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String httpMethod) {
        return Result.success(apiDefinitionService.page(page, pageSize, name, httpMethod));
    }

    /**
     *查询接口详细信息
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result<ApiDefinitionVO> detail(@PathVariable Long id) {
        return Result.success(apiDefinitionService.detail(id));
    }

    /**
     * 更改接口信息
     * @param id
     * @param dto
     * @return
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ApiDefinitionUpdateDTO dto) {
        apiDefinitionService.update(id, dto);
        return Result.success();
    }

    /**
     * 删除接口
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        apiDefinitionService.delete(id);
        return Result.success();
    }

    /**
     * 接口文档导入
     * @param file
     * @return
     */
    @PostMapping("/import")
    public Result<ApiImportResultVO> importDocument(@RequestParam("file") MultipartFile file) {
        return Result.success(apiDefinitionService.importDocument(file));
    }
}
