package com.lun.luncodesandbox.mode;

import lombok.Data;

/**
 * 进程执行信息
 *
 * @Date 2025/10/2
 */
@Data
public class ExecuteMessage {
    private String message;
    private Integer exitValue;
    private String errorMessage;
    private Long time;
    private Long memory;
}
