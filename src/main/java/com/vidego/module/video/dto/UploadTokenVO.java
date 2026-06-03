package com.vidego.module.video.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadTokenVO {

    /** 预签名 PUT URL，前端直接 PUT 文件至此地址 */
    private String uploadUrl;

    /** MinIO 对象键，创建视频记录时需要提交 */
    private String objectKey;
}
