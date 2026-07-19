package com.vidego.module.admin;

/**
 * 文本内容审核服务
 *
 * <p>由 {@link AdminVideoAuditConsumer} 在异步审核流程中调用，
 * 对视频的标题、描述、标签等文本字段进行敏感词检测。</p>
 *
 * <p>当前实现基于本地敏感词列表的简单匹配；
 * 生产环境可对接第三方内容安全 API（如阿里云内容安全、腾讯云天御）。</p>
 */
public interface TextAuditService {

    /**
     * 对一段或多段文本进行审核
     *
     * @param texts 待审核文本（标题 / 描述 / 标签等，可为 null）
     * @return 审核结果
     */
    AuditResult audit(String... texts);

    /**
     * 文本审核结果
     */
    final class AuditResult {
        private final boolean rejected;
        private final String reason;

        private AuditResult(boolean rejected, String reason) {
            this.rejected = rejected;
            this.reason = reason;
        }

        /** 审核通过 */
        public static AuditResult pass() {
            return new AuditResult(false, null);
        }

        /** 审核不通过，需指定原因 */
        public static AuditResult reject(String reason) {
            return new AuditResult(true, reason);
        }

        public boolean isRejected() {
            return rejected;
        }

        public String getReason() {
            return reason;
        }
    }
}
