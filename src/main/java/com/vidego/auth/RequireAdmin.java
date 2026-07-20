package com.vidego.auth;

import java.lang.annotation.*;

/**
 * 管理员权限校验注解
 *
 * <p>标注在 Controller 方法或类上，
 * 由 {@link com.vidego.auth.AdminAuthAspect} 切面在方法执行前检查当前用户是否为管理员（role=1）。
 * 非管理员访问将抛出 {@link com.vidego.common.result.ErrorCode#FORBIDDEN}。</p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * {@code
 * @RestController
 * @RequestMapping("/api/admin")
 * @RequireAdmin
 * public class AdminController {
 *
 *     @GetMapping("/users")
 *     public Result<List<UserVO>> listUsers() { ... }
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdmin {
}
