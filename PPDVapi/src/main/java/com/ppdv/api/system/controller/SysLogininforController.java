package com.ppdv.api.system.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.core.redis.RedisCache;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.system.domain.SysLogininfor;
import com.ppdv.api.system.repository.SysLogininforRepository;
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
 * 登录日志记录：查询 / 删除 / 清空 / 导出 Excel / 解锁用户。
 */
@RestController
@RequestMapping("/monitor/logininfor")
public class SysLogininforController extends BaseController {

    /** 导出上限 */
    private static final int EXPORT_LIMIT = 10000;

    /** 密码错误次数 Redis key 前缀 */
    private static final String PWD_ERR_CNT_KEY = "pwd_err_cnt:";

    /** 时间列格式（Excel 时间列与前端时间参数解析共用） */
    private static final DateTimeFormatter CSV_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysLogininforRepository logininforRepository;
    private final RedisCache redisCache;

    public SysLogininforController(SysLogininforRepository logininforRepository, RedisCache redisCache) {
        this.logininforRepository = logininforRepository;
        this.redisCache = redisCache;
    }

    @PreAuthorize("@ss.hasPermi('monitor:logininfor:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysLogininfor filter, PageQuery pageQuery) {
        return getDataTable(logininforRepository.findAll(buildSpec(filter), pageQuery.toPageable()));
    }

    @PreAuthorize("@ss.hasPermi('monitor:logininfor:remove')")
    @DeleteMapping("/{infoIds}")
    public AjaxResult remove(@PathVariable Long[] infoIds) {
        for (Long infoId : infoIds) {
            logininforRepository.deleteById(infoId);
        }
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('monitor:logininfor:remove')")
    @DeleteMapping("/clean")
    public AjaxResult clean() {
        logininforRepository.deleteAllInBatch();
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('monitor:logininfor:export')")
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(SysLogininfor filter) {
        Page<SysLogininfor> page = logininforRepository.findAll(buildSpec(filter), PageRequest.of(0, EXPORT_LIMIT));
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("登录日志");
            XSSFRow headRow = sheet.createRow(0);
            String[] headers = {"访问编号", "用户名称", "登录IP地址", "登录地点", "浏览器", "操作系统", "登录状态", "提示消息", "访问时间"};
            for (int i = 0; i < headers.length; i++) {
                setCellText(headRow, i, headers[i]);
            }
            int rowNum = 1;
            for (SysLogininfor log : page.getContent()) {
                XSSFRow row = sheet.createRow(rowNum++);
                setCellText(row, 0, log.getInfoId());
                setCellText(row, 1, log.getUserName());
                setCellText(row, 2, log.getIpaddr());
                setCellText(row, 3, log.getLoginLocation());
                setCellText(row, 4, log.getBrowser());
                setCellText(row, 5, log.getOs());
                setCellText(row, 6, log.getStatus());
                setCellText(row, 7, log.getMsg());
                setCellText(row, 8, log.getLoginTime() == null ? null : log.getLoginTime().format(CSV_TIME_FORMATTER));
            }
            workbook.write(out);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=login_log.xlsx")
                    .body(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("生成登录日志 Excel 失败: " + e.getMessage(), e);
        }
    }

    /** 解锁用户登录状态：前端 api/monitor/logininfor.js 的 unlockLogininfor 以 GET 调用本接口。 */
    @PreAuthorize("@ss.hasPermi('monitor:logininfor:unlock')")
    @GetMapping("/unlock/{userName}")
    public AjaxResult unlock(@PathVariable String userName) {
        redisCache.delete(PWD_ERR_CNT_KEY + userName);
        return AjaxResult.success();
    }

    /** 组装查询条件：ipaddr/userName 模糊，status 精确，loginTime 时间范围。 */
    private Specification<SysLogininfor> buildSpec(SysLogininfor filter) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (filter != null) {
                if (StringUtils.hasText(filter.getIpaddr())) {
                    ps.add(cb.like(root.get("ipaddr"), "%" + filter.getIpaddr() + "%"));
                }
                if (StringUtils.hasText(filter.getUserName())) {
                    ps.add(cb.like(root.get("userName"), "%" + filter.getUserName() + "%"));
                }
                if (StringUtils.hasText(filter.getStatus())) {
                    ps.add(cb.equal(root.get("status"), filter.getStatus()));
                }
                LocalDateTime begin = parseBegin(filter.getParams());
                if (begin != null) {
                    ps.add(cb.greaterThanOrEqualTo(root.get("loginTime"), begin));
                }
                LocalDateTime end = parseEnd(filter.getParams());
                if (end != null) {
                    ps.add(cb.lessThanOrEqualTo(root.get("loginTime"), end));
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
