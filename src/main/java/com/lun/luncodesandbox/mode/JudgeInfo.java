package com.lun.luncodesandbox.mode;

import lombok.Data;

/**
 * @Description
 * @Date 2025/9/18
 * 判题信息
 */
@Data
public class JudgeInfo {
    /**
     * 程序执行信息
     */
    private String message;
    /**
     * 消耗内存
     */
    private Long memory;
    /**
     * 消耗时间 kb
     */
    private Long time;
}
