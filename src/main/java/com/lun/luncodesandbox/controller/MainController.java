package com.lun.luncodesandbox.controller;

import com.lun.luncodesandbox.JavaNativeCodeSandbox;
import com.lun.luncodesandbox.mode.ExecuteCodeRequest;
import com.lun.luncodesandbox.mode.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @Description
 *
 */
@RestController("/")
public class MainController {
    /***
    服务需要做权限校验，不能在不做校验的情况下直接发到公网，不安全
    在调用方和服务提供方之间约定一个字符串（最好加密），作为请求头，服务提供方收到请求后，判断请求头是否一致，一致则进行业务处理，不一致则拒绝处理
    优点：简单，适合服务内部之间相互调用（相对可信的环境内部调用）
    缺点：不够灵活，如果key更换或者泄露，需要重启代码
     ***/

    //定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auto";
    private static final String AUTH_REQUEST_SECRET = "secretKey";
    @Resource
    private JavaNativeCodeSandbox javaNativeCodeSandbox;

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request, HttpServletResponse response){
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if(!AUTH_REQUEST_SECRET.equals(authHeader)){
            response.setStatus(403);
            return null;
        }
        if(executeCodeRequest == null){
            throw new RuntimeException("参数为空");
        }
        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }
}
