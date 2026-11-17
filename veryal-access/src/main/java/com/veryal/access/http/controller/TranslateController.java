package com.veryal.access.http.controller;

import com.veryal.access.http.dto.TranslateRequest;
import com.veryal.access.http.dto.TranslateResponse;
import com.veryal.access.http.translate.BaiduTranslator;
import com.veryal.common.core.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文本翻译：服务端代理百度翻译，密钥不出后端。
 */
@Tag(name = "翻译")
@RestController
@RequestMapping("/api/v1/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final BaiduTranslator translator;

    @Operation(summary = "翻译文本", description = "`to` 为百度翻译语言代码（en/zh/jp/kor…），缺省 en。相同原文与目标语言命中进程内缓存。")
    @PostMapping
    public ApiResponse<TranslateResponse> translate(@Valid @RequestBody TranslateRequest request) {
        BaiduTranslator.Result result = translator.translate(request.text(), request.to());
        return ApiResponse.ok(new TranslateResponse(result.from(), result.to(), result.text()));
    }
}
