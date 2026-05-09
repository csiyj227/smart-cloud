package com.smart.auth.captcha;

import cn.hutool.core.util.IdUtil;
import com.wf.captcha.ArithmeticCaptcha;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Captcha service supporting multiple captcha types:
 * - arithmetic (计算题)
 * - char (字符)
 * - spec (干扰字符)
 * - sliding (滑块验证码 - requires frontend component)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    private static final String SLIDING_CAPTCHA_KEY_PREFIX = "captcha:sliding:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${smart.captcha.enabled:true}")
    private boolean captchaEnabled;

    @Value("${smart.captcha.expiration:2}")
    private int captchaExpirationMinutes;

    @Value("${smart.captcha.type:char}")
    private String captchaType;

    @Value("${smart.captcha.length:4}")
    private int captchaLength;

    @Value("${smart.captcha.width:120}")
    private int captchaWidth;

    @Value("${smart.captcha.height:40}")
    private int captchaHeight;

    /**
     * Generate a captcha and return as base64 image + uuid.
     */
    public CaptchaResult generate() {
        if (!captchaEnabled) {
            return new CaptchaResult(IdUtil.simpleUUID(), "", "disabled");
        }

        String uuid = IdUtil.simpleUUID();
        String redisKey = CAPTCHA_KEY_PREFIX + uuid;

        if ("arithmetic".equals(captchaType)) {
            // 自行生成算术验证码，避免 EasyCaptcha ArithmeticCaptcha 依赖 Nashorn（JDK 15+ 已移除）
            ArithmeticExpression expression = generateArithmeticExpression();
            SpecCaptcha captcha = new SpecCaptcha(captchaWidth, captchaHeight);
            captcha.setCharType(Captcha.TYPE_DEFAULT);
            captcha.setLen(expression.display().length());
            // SpecCaptcha 只能展示随机字符，这里用文本直接画图
            String imageBase64 = renderArithmeticImage(expression.display());
            redisTemplate.opsForValue().set(redisKey, String.valueOf(expression.answer()), Duration.ofMinutes(captchaExpirationMinutes));
            log.debug("Generated arithmetic captcha: uuid={}, expression={}", uuid, expression.display());
            return new CaptchaResult(uuid, imageBase64, captchaType);
        }

        Captcha captcha = createCaptcha();
        redisTemplate.opsForValue().set(redisKey, captcha.text(), Duration.ofMinutes(captchaExpirationMinutes));
        String imageBase64 = captcha.toBase64();
        log.debug("Generated captcha: type={}, uuid={}", captchaType, uuid);
        return new CaptchaResult(uuid, imageBase64, captchaType);
    }

    /**
     * Verify captcha code.
     *
     * @param uuid the captcha uuid
     * @param code the user input code
     * @return true if valid
     */
    public boolean verify(String uuid, String code) {
        if (!captchaEnabled) {
            return true;
        }

        if (uuid == null || code == null) {
            return false;
        }

        String redisKey = CAPTCHA_KEY_PREFIX + uuid;
        Object cachedCode = redisTemplate.opsForValue().get(redisKey);

        if (cachedCode == null) {
            log.debug("Captcha expired or not found: uuid={}", uuid);
            return false;
        }

        // Delete after verification (one-time use)
        redisTemplate.delete(redisKey);

        boolean result = cachedCode.toString().equalsIgnoreCase(code);
        log.debug("Captcha verification: uuid={}, result={}", uuid, result);
        return result;
    }

    /**
     * Generate sliding captcha (background image with slider position).
     */
    public SlidingCaptchaResult generateSliding() {
        String uuid = IdUtil.simpleUUID();

        // Generate a random slider position
        int sliderX = (int) (Math.random() * (captchaWidth - 40)) + 20;

        // Create background image with text
        SpecCaptcha captcha = new SpecCaptcha(captchaWidth, captchaHeight);
        captcha.setCharType(Captcha.TYPE_DEFAULT);
        captcha.setLen(4);

        String redisKey = SLIDING_CAPTCHA_KEY_PREFIX + uuid;
        redisTemplate.opsForValue().set(redisKey, sliderX, Duration.ofMinutes(captchaExpirationMinutes));

        String imageBase64 = captcha.toBase64();

        log.debug("Generated sliding captcha: uuid={}, sliderX={}", uuid, sliderX);
        return new SlidingCaptchaResult(uuid, imageBase64, sliderX);
    }

    /**
     * Verify sliding captcha.
     *
     * @param uuid     the captcha uuid
     * @param sliderX  the slider position
     * @return true if valid (tolerance: +/- 5 pixels)
     */
    public boolean verifySliding(String uuid, int sliderX) {
        if (!captchaEnabled) {
            return true;
        }

        if (uuid == null) {
            return false;
        }

        String redisKey = SLIDING_CAPTCHA_KEY_PREFIX + uuid;
        Object cachedX = redisTemplate.opsForValue().get(redisKey);

        if (cachedX == null) {
            return false;
        }

        redisTemplate.delete(redisKey);

        int expectedX = Integer.parseInt(cachedX.toString());
        boolean result = Math.abs(expectedX - sliderX) <= 5;

        log.debug("Sliding captcha verification: uuid={}, expected={}, actual={}, result={}",
                uuid, expectedX, sliderX, result);
        return result;
    }

    /**
     * 创建字符/干扰字符类验证码（不含 arithmetic，arithmetic 在 generate() 中单独处理）。
     */
    private Captcha createCaptcha() {
        SpecCaptcha captcha = new SpecCaptcha(captchaWidth, captchaHeight);
        if ("char".equals(captchaType)) {
            captcha.setCharType(Captcha.TYPE_ONLY_CHAR);
        } else {
            captcha.setCharType(Captcha.TYPE_DEFAULT);
        }
        captcha.setLen(captchaLength);
        captcha.setFont(new Font("Verdana", Font.ITALIC, 24));
        return captcha;
    }

    // ---- 算术验证码：自行实现，避免 EasyCaptcha ArithmeticCaptcha 依赖 Nashorn ----

    private record ArithmeticExpression(String display, int answer) {}

    private ArithmeticExpression generateArithmeticExpression() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int operandA = random.nextInt(1, 30);
        int operandB = random.nextInt(1, 30);
        int operatorIndex = random.nextInt(3);

        String operator;
        int answer;
        switch (operatorIndex) {
            case 0 -> { operator = "+"; answer = operandA + operandB; }
            case 1 -> {
                operator = "-";
                // 确保结果非负
                if (operandA < operandB) { int tmp = operandA; operandA = operandB; operandB = tmp; }
                answer = operandA - operandB;
            }
            default -> {
                operator = "×";
                operandA = random.nextInt(1, 10);
                operandB = random.nextInt(1, 10);
                answer = operandA * operandB;
            }
        }
        String display = operandA + operator + operandB + "=?";
        return new ArithmeticExpression(display, answer);
    }

    /**
     * 用 Java2D 将算术表达式渲染为 base64 图片（带干扰线），替代 EasyCaptcha 的 ArithmeticCaptcha。
     */
    private String renderArithmeticImage(String text) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                captchaWidth, captchaHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();

        // 启用抗锯齿
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        // 背景
        graphics.setColor(new Color(240, 240, 240));
        graphics.fillRect(0, 0, captchaWidth, captchaHeight);

        // 干扰线
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 4; i++) {
            graphics.setColor(new Color(random.nextInt(180, 230), random.nextInt(180, 230), random.nextInt(180, 230)));
            graphics.drawLine(random.nextInt(captchaWidth), random.nextInt(captchaHeight),
                    random.nextInt(captchaWidth), random.nextInt(captchaHeight));
        }

        // 绘制文字
        graphics.setFont(new Font("Verdana", Font.BOLD, 22));
        graphics.setColor(new Color(random.nextInt(60, 120), random.nextInt(60, 120), random.nextInt(60, 120)));
        java.awt.FontMetrics fontMetrics = graphics.getFontMetrics();
        int textX = (captchaWidth - fontMetrics.stringWidth(text)) / 2;
        int textY = (captchaHeight + fontMetrics.getAscent() - fontMetrics.getDescent()) / 2;
        graphics.drawString(text, textX, textY);
        graphics.dispose();

        // 转 base64
        try {
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", outputStream);
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (java.io.IOException e) {
            log.error("Failed to render arithmetic captcha image", e);
            throw new RuntimeException("Failed to render captcha image", e);
        }
    }

    public record CaptchaResult(String uuid, String image, String type) {}

    public record SlidingCaptchaResult(String uuid, String image, int sliderX) {}
}