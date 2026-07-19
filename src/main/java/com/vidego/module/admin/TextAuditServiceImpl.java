package com.vidego.module.admin;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文本审核服务实现
 *
 * <p>基于 <a href="https://github.com/houbb/sensitive-word">sensitive-word</a> 的 DFA 敏感词引擎，
 * 内置默认词库，支持繁简转换、英文大小写、半角全角、数字转换等归一化处理。</p>
 *
 * <p>生产环境可进一步：</p>
 * <ul>
 *   <li>通过 {@code SensitiveWordBs.newInstance().wordResult()} 自定义词库来源（DB / Redis）</li>
 *   <li>对接第三方内容安全 API 做图片/视频画面审核</li>
 * </ul>
 */
@Slf4j
@Service
public class TextAuditServiceImpl implements TextAuditService {

    /** DFA 敏感词引擎（线程安全，初始化一次） */
    private final SensitiveWordBs sensitiveWordBs = SensitiveWordBs.newInstance().init();

    @Override
    public AuditResult audit(String... texts) {
        if (texts == null || texts.length == 0) {
            return AuditResult.pass();
        }

        for (String text : texts) {
            if (text == null || text.isEmpty()) {
                continue;
            }
            List<String> matchedWords = sensitiveWordBs.findAll(text);
            if (!matchedWords.isEmpty()) {
                String firstWord = matchedWords.get(0);
                log.warn("Text audit rejected: matched sensitive word='{}', all={}", firstWord, matchedWords);
                return AuditResult.reject("文本包含敏感词: " + firstWord);
            }
        }
        return AuditResult.pass();
    }
}
