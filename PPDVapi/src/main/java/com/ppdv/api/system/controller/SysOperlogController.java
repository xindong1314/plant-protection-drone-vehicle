package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.system.domain.SysOperLog;
import com.ppdv.api.system.repository.SysOperLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 操作日志记录：查询 / 删除 / 清空 / 导出 Excel。
 */
@RestController
@RequestMapping("/monitor/operlog")
public class SysOperlogController extends BaseController {

    /** 导出上限 */
    private static final int EXPORT_LIMIT = 10000;

    /** 时间列格式（Excel 时间列与前端时间参数解析共用） */
    private static final DateTimeFormatter CSV_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysOperLogRepository operLogRepository;

    public SysOperlogController(SysOperLogRepository operLogRepository) {
        this.operLogRepository = operLogRepository;
    }

    @PreAuthorize("@ss.hasPermi('monitor:operlog:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysOperLog filter, PageQuery pageQuery) {
        return getDataTable(operLogRepository.findAll(buildSpec(filter), pageQuery.toPageable()));
    }

    @PreAuthorize("@ss.hasPermi('monitor:operlog:remove')")
    @DeleteMapping("/{operIds}")
    public AjaxResult remove(@PathVariable Long[] operIds) {
        for (Long operId : operIds) {
            operLogRepository.deleteById(operId);
        }
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('monitor:operlog:remove')")
    @DeleteMapping("/clean")
    public AjaxResult clean() {
        operLogRepository.deleteAllInBatch();
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('monitor:operlog:export')")
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(SysOperLog filter) {
        Page<SysOperLog> page = operLogRepository.findAll(buildSpec(filter), PageRequest.of(0, EXPORT_LIMIT));
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("操作日志");
            XSSFRow headRow = sheet.createRow(0);
            String[] headers = {"日志编号", "系统模块", "操作类型", "方法名称", "请求方式", "操作人员", "部门名称", "请求URL", "主机地址", "操作地点", "请求参数", "返回参数", "状态", "错误消息", "操作时间", "消耗时间(毫秒)"};
            for (int i = 0; i < headers.length; i++) {
                setCellText(headRow, i, headers[i]);
            }
            int rowNum = 1;
            for (SysOperLog log : page.getContent()) {
                XSSFRow row = sheet.createRow(rowNum++);
                setCellText(row, 0, log.getOperId());
                setCellText(row, 1, log.getTitle());
                setCellText(row, 2, log.getBusinessType());
                setCellText(row, 3, log.getMethod());
                setCellText(row, 4, log.getRequestMethod());
                setCellText(row, 5, log.getOperName());
                setCellText(row, 6, log.getDeptName());
                setCellText(row, 7, log.getOperUrl());
                setCellText(row, 8, log.getOperIp());
                setCellText(row, 9, log.getOperLocation());
                setCellText(row, 10, log.getOperParam());
                setCellText(row, 11, log.getJsonResult());
                setCellText(row, 12, log.getStatus());
                setCellText(row, 13, log.getErrorMsg());
                setCellText(row, 14, log.getOperTime() == null ? null : log.getOperTime().format(CSV_TIME_FORMATTER));
                setCellText(row, 15, log.getCostTime());
            }
            workbook.write(out);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=oper_log.xlsx")
                    .body(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("生成操作日志 Excel 失败: " + e.getMessage(), e);
        }
    }

    /** 组装查询条件：operIp/title/operName 模糊，businessType/status 精确，operTime 时间范围。 */
    private Specification<SysOperLog> buildSpec(SysOperLog filter) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (filter != null) {
                if (StringUtils.hasText(filter.getOperIp())) {
                    ps.add(cb.like(root.get("operIp"), "%" + filter.getOperIp() + "%"));
                }
                if (StringUtils.hasText(filter.getTitle())) {
                    ps.add(cb.like(root.get("title"), "%" + filter.getTitle() + "%"));
                }
                if (StringUtils.hasText(filter.getOperName())) {
                    ps.add(cb.like(root.get("operName"), "%" + filter.getOperName() + "%"));
                }
                if (filter.getBusinessType() != null) {
                    ps.add(cb.equal(root.get("businessType"), filter.getBusinessType()));
                }
                if (filter.getStatus() != null) {
                    ps.add(cb.equal(root.get("status"), filter.getStatus()));
                }
                LocalDateTime begin = parseBegin(filter.getParams());
                if (begin != null) {
                    ps.add(cb.greaterThanOrEqualTo(root.get("operTime"), begin));
                }
                LocalDateTime end = parseEnd(filter.getParams());
                if (end != null) {
                    ps.add(cb.lessThanOrEqualTo(root.get("operTime"), end));
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /** 从 params[beginTime]（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）解析起始时间（当天 00:00:00），缺省或非法值返回 null。 */
    private LocalDateTime parseBegin(Map<String, Object> params) {
        Object value = params == null ? null : params.get("beginTime");
        if (value == null || !StringUtils.hasText(value.toString())) {
            return null;
        }
        try {
            return LocalDate.parse(value.toString()).atStartOfDay();
        } catch (DateTimeParseException e) {
            // 前端日期范围控件传参为 yyyy-MM-dd HH:mm:ss，兜底取其日期部分
            try {
                return LocalDateTime.parse(value.toString(), CSV_TIME_FORMATTER).toLocalDate().atStartOfDay();
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    /** 从 params[endTime]（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）解析截止时间（当天 23:59:59），缺省或非法值返回 null。 */
    private LocalDateTime parseEnd(Map<String, Object> params) {
        Object value = params == null ? null : params.get("endTime");
        if (value == null || !StringUtils.hasText(value.toString())) {
            return null;
        }
        try {
            return LocalDate.parse(value.toString()).atTime(23, 59, 59);
        } catch (DateTimeParseException e) {
            // 前端日期范围控件传参为 yyyy-MM-dd HH:mm:ss，兜底取其日期部分
            try {
                return LocalDateTime.parse(value.toString(), CSV_TIME_FORMATTER).toLocalDate().atTime(23, 59, 59);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    /** 写入 Excel 文本单元格：null 转空串，统一按文本写入避免长编号精度丢失。 */
    private static void setCellText(XSSFRow row, int col, Object value) {
        row.createCell(col).setCellValue(value == null ? "" : value.toString());
    }
}
