package com.quality.guard.module.ai.report.mapper;

import com.quality.guard.module.ai.report.model.ApiRiskRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 报告专用查询：按接口聚合最近一次执行结果
 */
public interface ReportRiskMapper {

    /**
     * 取每个接口最近一次结果为 FAILED/ERROR 的接口，ERROR 优先，最多返回 limit 条
     * 最近一次按 test_result 自增 id 判定；已逻辑删除的用例/接口不参与
     */
    @Select("""
            SELECT a.id AS api_id,
                   a.name AS name,
                   a.http_method AS method,
                   a.path AS path,
                   r.id AS result_id,
                   r.status AS status
            FROM test_result r
            JOIN test_case c ON c.id = r.case_id AND c.deleted = 0
            JOIN api_definition a ON a.id = c.api_id AND a.deleted = 0
            WHERE r.id = (
                SELECT MAX(r2.id)
                FROM test_result r2
                JOIN test_case c2 ON c2.id = r2.case_id AND c2.deleted = 0
                WHERE c2.api_id = a.id
            )
              AND r.status IN ('FAILED', 'ERROR')
            ORDER BY CASE WHEN r.status = 'ERROR' THEN 0 ELSE 1 END, r.id DESC
            LIMIT #{limit}
            """)
    List<ApiRiskRow> selectRiskApis(@Param("limit") int limit);
}
