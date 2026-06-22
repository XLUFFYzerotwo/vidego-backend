package com.vidego.module.danmaku;

import com.vidego.common.exception.BusinessException;
import com.vidego.common.result.ErrorCode;
import com.vidego.module.danmaku.dto.DanmakuCreateRequest;
import com.vidego.module.danmaku.dto.DanmakuVO;
import com.vidego.module.danmaku.entity.Danmaku;
import com.vidego.module.danmaku.mapper.DanmakuMapper;
import com.vidego.module.user.entity.User;
import com.vidego.module.user.mapper.UserMapper;
import com.vidego.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DanmakuServiceImpl implements DanmakuService {
    private final DanmakuMapper danmakuMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_KEY = "danmaku:rate_limit:%d";
    private static final int RATE_LIMIT_SECONDS = 2;
    private static final int RATE_LIMIT_COUNT = 1;

    @Override
    public DanmakuVO createDanmaku(Long userId, DanmakuCreateRequest request) {
        if (!canSend(userId)) {
            throw new BusinessException(ErrorCode.USER_SEND_FREQUENTLY,"发送过于频繁，请稍后再试");
        }

        Danmaku danmaku = new Danmaku();
        danmaku.setVideoId(request.getVideoId());
        danmaku.setUserId(userId);
        danmaku.setContent(filterContent(request.getContent()));
        danmaku.setTime(request.getTime());
        danmaku.setColor(request.getColor());
        danmaku.setType(request.getType());
        danmaku.setStatus(1);

        danmakuMapper.insert(danmaku);



        // 更新速率限制
        String key = String.format(RATE_LIMIT_KEY, userId);
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(RATE_LIMIT_SECONDS));

        return convertToVO(danmaku);
    }

    @Override
    public List<DanmakuVO> getDanmakuByVideoId(Long videoId, Float startTime, Float endTime) {

        List<Danmaku> list = danmakuMapper.selectByVideoIdAndTimeRange(videoId, startTime, endTime);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public boolean canSend(Long userId) {
        String key = String.format(RATE_LIMIT_KEY, userId);
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(RATE_LIMIT_SECONDS)));
    }

    private String filterContent(String content) {
        // 简单敏感词过滤（实际项目中应使用专业敏感词库）
        String[] sensitiveWords = {"我日", "cnm"};
        String result = content;
        for (String word : sensitiveWords) {
            result = result.replace(word, "*".repeat(word.length()));
        }
        return result;
    }

    private DanmakuVO convertToVO(Danmaku danmaku) {
        DanmakuVO vo = new DanmakuVO();
        vo.setId(danmaku.getId());
        vo.setVideoId(danmaku.getVideoId());
        vo.setUserId(danmaku.getUserId());
        vo.setContent(danmaku.getContent());
        vo.setTime(danmaku.getTime());
        vo.setColor(danmaku.getColor());
        vo.setType(danmaku.getType());
        vo.setCreatedAt(danmaku.getCreatedAt().toString());

        User user = userMapper.selectById(danmaku.getUserId());
        if (user != null) {
            UserVO userVO = new UserVO();
            userVO.setId(user.getId());
            userVO.setNickname(user.getNickname());
            userVO.setAvatar(user.getAvatar());
            vo.setUser(userVO);
        }

        return vo;
    }
}
