package com.quality.guard.module.gate.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quality.guard.common.result.PageResult;
import com.quality.guard.common.result.Result;
import com.quality.guard.module.gate.dto.GateCheckDTO;
import com.quality.guard.module.gate.entity.QualityGateRecord;
import com.quality.guard.module.gate.mapper.QualityGateRecordMapper;
import com.quality.guard.module.gate.service.GateCheckService;
import com.quality.guard.module.gate.vo.GateRecordVO;
import com.quality.guard.module.gate.vo.GateResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 质量门禁接口：阈值判定 + 历史记录查询
 */
@RestController
@RequestMapping("/api/gates")
@RequiredArgsConstructor
public class GateController {

    private final GateCheckService gateCheckService;
    private final QualityGateRecordMapper gateRecordMapper;

    /** 执行门禁检查（每次调用都会留档） */
    @PostMapping("/check")
    public Result<GateResultVO> check(@Valid @RequestBody GateCheckDTO dto) {
        return Result.success(gateCheckService.check(dto));
    }

    /** 分页查询门禁检查历史 */
    @GetMapping("/records")
    public Result<PageResult<GateRecordVO>> records(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long runId) {
        Page<QualityGateRecord> result = gateRecordMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<QualityGateRecord>()
                        .eq(runId != null, QualityGateRecord::getRunId, runId)
                        .orderByDesc(QualityGateRecord::getId));
        List<GateRecordVO> records = result.getRecords().stream().map(this::toVO).toList();
        return Result.success(PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records));
    }

    private GateRecordVO toVO(QualityGateRecord record) {
        GateRecordVO vo = new GateRecordVO();
        vo.setId(record.getId());
        vo.setRunId(record.getRunId());
        vo.setPassed(Boolean.TRUE.equals(record.getPassed()));
        vo.setScore(record.getScore());
        vo.setThresholds(record.getThresholdSnapshot());
        vo.setViolations(record.getViolations());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }
}
