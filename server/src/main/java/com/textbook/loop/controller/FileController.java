package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.common.BizException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 存储接口：图片与视频统一上传到本地 data/uploads，按 年月 分目录存放。
 * 返回可直接用于商品封面、七张实拍图、视频与认证材料的 URL。
 *
 * <p>生产环境把 {@code app.upload-dir} 指向对象存储挂载目录，或替换为 OSS/S3 实现，接口不变。</p>
 */
@RestController
@RequestMapping("/v1/files")
public class FileController {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final List<String> ALLOWED_EXT = List.of(
            "jpg", "jpeg", "png", "webp", "gif", "bmp",
            "mp4", "mov", "m4v", "webm");

    private final Path root;

    public FileController(com.textbook.loop.config.AppProps props) {
        this.root = Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(defaultValue = "image") String kind) throws IOException {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的文件");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw BizException.badRequest("不支持的文件类型：" + (ext.isEmpty() ? "未知" : ext));
        }
        boolean video = List.of("mp4", "mov", "m4v", "webm").contains(ext);
        if (video != "video".equalsIgnoreCase(kind)) {
            throw BizException.badRequest(video ? "视频请以 kind=video 上传" : "图片请以 kind=image 上传");
        }

        String month = LocalDate.now().format(MONTH_FMT);
        Path dir = root.resolve(month);
        Files.createDirectories(dir);
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = dir.resolve(name);
        file.transferTo(target.toFile());

        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/v1/files/{month}/{name}")
                .buildAndExpand(month, name)
                .toUriString();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url);
        data.put("path", "/v1/files/" + month + "/" + name);
        data.put("name", name);
        data.put("size", file.getSize());
        data.put("contentType", file.getContentType());
        data.put("kind", video ? "video" : "image");
        return ApiResponse.ok(data);
    }

    @GetMapping("/{month}/{name}")
    public ResponseEntity<Resource> get(@PathVariable String month, @PathVariable String name) {
        if (!month.matches("\\d{6}") || !name.matches("[A-Za-z0-9]+\\.(jpg|jpeg|png|webp|gif|bmp|mp4|mov|m4v|webm)")) {
            throw BizException.badRequest("文件名不合法");
        }
        Path file = root.resolve(month).resolve(name).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw BizException.notFound("文件不存在");
        }
        MediaType type;
        try {
            type = MediaType.parseMediaType(Files.probeContentType(file));
        } catch (IOException e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(new FileSystemResource(file));
    }
}
