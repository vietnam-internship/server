package com.fptis.intern.server.presentation.user;

import com.fptis.intern.server.application.user.UserService;
import com.fptis.intern.server.global.annotation.RequireAuth;
import com.fptis.intern.server.global.annotation.UserId;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.global.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CookieUtil cookieUtil;

    @RequireAuth
    @DeleteMapping("/me")
    public ApiResponse<?> withdraw(@UserId Long userId, HttpServletResponse response) {
        userService.withdraw(userId);
        cookieUtil.clearTokenCookies(response);
        return ApiResponse.success();
    }
}
