package com.ppdv.api.inspection.controller;

import com.ppdv.api.common.AjaxResult;
import com.ppdv.api.common.page.PageQuery;
import com.ppdv.api.common.page.TableDataInfo;
import com.ppdv.api.common.web.BaseController;
import com.ppdv.api.inspection.domain.DetectionResult;
import com.ppdv.api.inspection.domain.InspectionRecord;
import com.ppdv.api.inspection.service.InspectionService;
import com.ppdv.api.storage.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ppdv/inspection")
public class InspectionController extends BaseController {

    private final InspectionService service;
    private final FileStorageService fileStorage;

    public InspectionController(InspectionService service, FileStorageService fileStorage) {
        this.service = service;
        this.fileStorage = fileStorage;
    }

    // ---- 巡检记录 ----

    @PreAuthorize("@ss.hasPermi('ppdv:inspection:query')")
    @GetMapping("/list")
    public TableDataInfo list(PageQuery pageQuery) {
        Page<InspectionRecord> page = service.listRecords(pageQuery.toPageable());
        return getDataTable(page);
    }

    @PreAuthorize("@ss.hasPermi('ppdv:inspection:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        InspectionRecord record = service.getRecord(id);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("record", record);
        ajax.put("images", service.getImages(id));
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('ppdv:inspection:upload')")
    @PostMapping("/start")
    public AjaxResult startInspection(@RequestBody Map<String, Object> body) {
        String deviceId = (String) body.get("deviceId");
        Long areaId = body.get("areaId") != null ? ((Number) body.get("areaId")).longValue() : null;
        InspectionRecord record = service.startInspection(deviceId, areaId);
        return AjaxResult.success("巡检已开始", record);
    }

    @PreAuthorize("@ss.hasPermi('ppdv:inspection:upload')")
    @PutMapping("/{id}/complete")
    public AjaxResult completeInspection(@PathVariable Long id) {
        service.completeInspection(id);
        return AjaxResult.success("巡检已完成");
    }

    // ---- 图片 ----

    @PreAuthorize("@ss.hasPermi('ppdv:inspection:upload')")
    @PostMapping("/image")
    public AjaxResult addImage(@RequestBody Map<String, Object> body) {
        Long inspectionId = ((Number) body.get("inspectionId")).longValue();
        Long areaId = body.get("areaId") != null ? ((Number) body.get("areaId")).longValue() : null;
        String filePath = (String) body.get("filePath");
        return AjaxResult.success(service.addImage(inspectionId, areaId, filePath));
    }

    /** 真正的文件上传：接收 MultipartFile，存磁盘，返回可访问 URL。 */
    @PreAuthorize("@ss.hasPermi('ppdv:inspection:upload')")
    @PostMapping("/image/upload")
    public AjaxResult uploadImage(@RequestParam("file") MultipartFile file,
                                  @RequestParam("inspectionId") Long inspectionId,
                                  @RequestParam(value = "areaId", required = false) Long areaId,
                                  @RequestParam(value = "deviceId", required = false) String deviceId) {
        if (file.isEmpty()) {
            return AjaxResult.error("文件不能为空");
        }
        String url = fileStorage.store(file, deviceId, areaId);
        var image = service.addImage(inspectionId, areaId, url);
        return AjaxResult.success("上传成功", image);
    }

    @PreAuthorize("@ss.hasPermi('ppdv:inspection:query')")
    @GetMapping("/image/{inspectionId}")
    public AjaxResult getImages(@PathVariable Long inspectionId) {
        return AjaxResult.success(service.getImages(inspectionId));
    }

    // ---- 识别结果 ----

    @PreAuthorize("@ss.hasPermi('ppdv:inspection:upload')")
    @PostMapping("/detection")
    public AjaxResult addDetection(@RequestBody DetectionResult result) {
        return AjaxResult.success(service.addDetection(result));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:inspection:upload')")
    @PostMapping("/detection/batch")
    public AjaxResult batchDetection(@RequestBody List<DetectionResult> results) {
        return AjaxResult.success(service.addDetections(results));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:inspection:query')")
    @GetMapping("/detection/image/{imageId}")
    public AjaxResult getDetectionsByImage(@PathVariable Long imageId) {
        return AjaxResult.success(service.getDetectionsByImage(imageId));
    }

    @PreAuthorize("@ss.hasPermi('ppdv:inspection:query')")
    @GetMapping("/detection/area/{areaId}")
    public AjaxResult getDetectionsByArea(@PathVariable Long areaId) {
        return AjaxResult.success(service.getDetectionsByArea(areaId));
    }
}
