package com.quality.guard.module.testexec.definition.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.PageResult;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.infrastructure.openapi.OpenApiDocumentParser;
import com.quality.guard.infrastructure.openapi.ParsedApi;
import com.quality.guard.infrastructure.openapi.ParsedApiDocument;
import com.quality.guard.module.testexec.definition.dto.ApiDefinitionCreateDTO;
import com.quality.guard.module.testexec.definition.dto.ApiDefinitionUpdateDTO;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.definition.service.ApiDefinitionService;
import com.quality.guard.module.testexec.definition.vo.ApiDefinitionVO;
import com.quality.guard.module.testexec.definition.vo.ApiImportResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDefinitionServiceImpl implements ApiDefinitionService {

    private final ApiDefinitionMapper apiDefinitionMapper;
    private final OpenApiDocumentParser openApiDocumentParser;

    /**
     * 创建接口
     * @param dto
     * @return
     */
    @Override
    public Long create(ApiDefinitionCreateDTO dto) {
        ApiDefinition entity = new ApiDefinition();
        entity.setName(dto.getName());
        entity.setHttpMethod(dto.getHttpMethod().toUpperCase());
        entity.setPath(dto.getPath());
        entity.setDescription(dto.getDescription());
        entity.setBaseUrl(dto.getBaseUrl());
        entity.setRequestSchema(dto.getRequestSchema());
        entity.setResponseSchema(dto.getResponseSchema());
        apiDefinitionMapper.insert(entity);
        return entity.getId();
    }

    /**
     * 分页查询接口
     * @param page
     * @param pageSize
     * @param name
     * @param httpMethod
     * @return
     */
    @Override
    public PageResult<ApiDefinitionVO> page(long page, long pageSize, String name, String httpMethod) {
        LambdaQueryWrapper<ApiDefinition> wrapper = new LambdaQueryWrapper<ApiDefinition>()
                .like(StringUtils.hasText(name), ApiDefinition::getName, name)
                .eq(StringUtils.hasText(httpMethod), ApiDefinition::getHttpMethod,
                        httpMethod == null ? null : httpMethod.toUpperCase())
                .orderByDesc(ApiDefinition::getId);
        Page<ApiDefinition> result = apiDefinitionMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<ApiDefinitionVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /**
     * 查询接口详细信息
     * @param id
     * @return
     */
    @Override
    public ApiDefinitionVO detail(Long id) {
        return toVO(requireById(id));
    }

    /**
     * 更新接口
     * @param id
     * @param dto
     */
    @Override
    public void update(Long id, ApiDefinitionUpdateDTO dto) {
        ApiDefinition entity = requireById(id);
        if (StringUtils.hasText(dto.getName())) {
            entity.setName(dto.getName());
        }
        if (StringUtils.hasText(dto.getHttpMethod())) {
            entity.setHttpMethod(dto.getHttpMethod().toUpperCase());
        }
        if (StringUtils.hasText(dto.getPath())) {
            entity.setPath(dto.getPath());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getBaseUrl() != null) {
            entity.setBaseUrl(dto.getBaseUrl());
        }
        if (dto.getRequestSchema() != null) {
            entity.setRequestSchema(dto.getRequestSchema());
        }
        if (dto.getResponseSchema() != null) {
            entity.setResponseSchema(dto.getResponseSchema());
        }
        apiDefinitionMapper.updateById(entity);
    }

    /**
     * 删除接口
     * @param id
     */
    @Override
    public void delete(Long id) {
        requireById(id);
        apiDefinitionMapper.deleteById(id);
    }

    /**
     * 导入接口文档
     * @param file
     * @return
     */
    @Override
    public ApiImportResultVO importDocument(MultipartFile file) {
        //非空判断
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }
        //文件合法判断
        String filename = file.getOriginalFilename();
        if (!isSupportedFile(filename)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅支持 .json / .yaml / .yml 文件");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "读取上传文件失败");
        }
        //利用接口文档解析器解析接口文档
        ParsedApiDocument document = openApiDocumentParser.parse(content);
         //设置导入接口文档的最终结果
        ApiImportResultVO result = new ApiImportResultVO();
        result.setDocumentTitle(document.getDocumentTitle());
        result.setDocumentVersion(document.getDocumentVersion());
        result.setTotalParsed(document.getApis().size());

        int imported = 0;
        int updated = 0;
        //遍历每个接口并按（方法 + 路径）去重入库：已存在则更新定义（保留原 ID），不存在则新增
        for (ParsedApi api : document.getApis()) {
            String method = api.getHttpMethod().toUpperCase();
            result.getMethodCounts().merge(method, 1, Integer::sum);
            try {
                //尝试根据方法和路径查询接口
                ApiDefinition existing = apiDefinitionMapper.selectOne(
                        new LambdaQueryWrapper<ApiDefinition>()
                                .eq(ApiDefinition::getHttpMethod, method)
                                .eq(ApiDefinition::getPath, api.getPath()),
                        false);
                //没查到就新增
                if (existing == null) {
                    apiDefinitionMapper.insert(toEntity(api));
                    imported++;
                    //查到就更新
                } else {
                    existing.setName(api.getName());
                    existing.setDescription(api.getDescription());
                    existing.setBaseUrl(api.getBaseUrl());
                    existing.setRequestSchema(api.getRequestSchema());
                    existing.setResponseSchema(api.getResponseSchema());
                    apiDefinitionMapper.updateById(existing);
                    updated++;
                }
            } catch (Exception e) {
                log.warn("接口入库失败: {} {} - {}", method, api.getPath(), e.getMessage());
                result.getFailures().add(new ApiImportResultVO.Failure(
                        method + " " + api.getPath(), e.getMessage()));
            }
        }
        result.setImported(imported);
        result.setUpdated(updated);
        result.setSkipped(0);
        return result;
    }

    /**
     * 详细查询接口
     * @param id
     * @return
     */
    private ApiDefinition requireById(Long id) {
        ApiDefinition entity = apiDefinitionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "接口定义不存在: " + id);
        }
        return entity;
    }
     //判断文件是否合法
    private boolean isSupportedFile(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml");
    }
     //转为实体对象
    private ApiDefinition toEntity(ParsedApi api) {
        ApiDefinition entity = new ApiDefinition();
        entity.setName(api.getName());
        entity.setHttpMethod(api.getHttpMethod().toUpperCase());
        entity.setPath(api.getPath());
        entity.setDescription(api.getDescription());
        entity.setBaseUrl(api.getBaseUrl());
        entity.setRequestSchema(api.getRequestSchema());
        entity.setResponseSchema(api.getResponseSchema());
        return entity;
    }
    //转为vo
    private ApiDefinitionVO toVO(ApiDefinition entity) {
        ApiDefinitionVO vo = new ApiDefinitionVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setHttpMethod(entity.getHttpMethod());
        vo.setPath(entity.getPath());
        vo.setDescription(entity.getDescription());
        vo.setBaseUrl(entity.getBaseUrl());
        vo.setRequestSchema(entity.getRequestSchema());
        vo.setResponseSchema(entity.getResponseSchema());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
