package com.veryal.access.http.support;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * multipart 小文件读取工具。
 *
 * <p>控制器里一律把它转成 byte[] 再交给应用服务：头像、表情这类小文件走内存，
 * 大文件本来就应该走分片上传，不需要在控制器层保留临时文件语义。</p>
 */
public final class MultipartFiles {

    private MultipartFiles() {
    }

    public static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "failed to read uploaded file");
        }
    }
}
