package com.quality.guard.module.testexec.definition.service;

import com.quality.guard.common.result.PageResult;
import com.quality.guard.module.testexec.definition.dto.ApiDefinitionCreateDTO;
import com.quality.guard.module.testexec.definition.dto.ApiDefinitionUpdateDTO;
import com.quality.guard.module.testexec.definition.vo.ApiDefinitionVO;
import com.quality.guard.module.testexec.definition.vo.ApiImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface ApiDefinitionService {

    Long create(ApiDefinitionCreateDTO dto);

    PageResult<ApiDefinitionVO> page(long page, long pageSize, String name, String httpMethod);

    ApiDefinitionVO detail(Long id);

    void update(Long id, ApiDefinitionUpdateDTO dto);

    void delete(Long id);

    ApiImportResultVO importDocument(MultipartFile file);
}
