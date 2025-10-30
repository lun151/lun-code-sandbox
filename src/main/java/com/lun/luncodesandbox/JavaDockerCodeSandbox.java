package com.lun.luncodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.lun.luncodesandbox.mode.ExecuteCodeRequest;
import com.lun.luncodesandbox.mode.ExecuteCodeResponse;
import com.lun.luncodesandbox.mode.ExecuteMessage;
import com.lun.luncodesandbox.mode.JudgeInfo;
import com.lun.luncodesandbox.utils.ProcessUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Description
 * 代码沙箱模板方法的实现
 */
@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {
    private static final long TIME_OUT = 5000L;
    private static final AtomicBoolean IMAGE_PULLED = new AtomicBoolean(false);


    public static void main(String[] args) {
        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        //  String code = ResourceUtil.readStr("testCode/unSafeCode/RunFileError.java", StandardCharsets.UTF_8);
        //  String code = ResourceUtil.readStr("testCode/simpleCompute/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        executeCodeRequest.setInputList(Arrays.asList("1 2", "2 3"));
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

    /**
     * 执行代码
     * @param userCodeFile
     * @param inputList
     * @return
     */
    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList)     {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        //创建容器，把文件复制到容器内，自定义容器，不同语言拉起不同镜像，执行别的语言的时候就拉取对应的镜像
        //在已有的容器上进行扩充，例如java容器(含jdk),把编译好的文件放进去执行，大大简化工作量
        //获取默认的docker Client
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        //拉取镜像 dockerhub
        String image = "openjdk:8-alpine";

        if(IMAGE_PULLED.compareAndSet(false, true)){
            //限制只拉取一次镜像， AtomicBoolean.compareAndSet()
            //检查当前值 false , 是否等于期待值 false ,符合则更新为 true
            //再次尝试拉取镜像，当前值为true ,不等于期待值 false ，则不执行

            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像: " + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            }catch (InterruptedException e){
                System.out.println("拉起镜像异常");
                throw new RuntimeException(e);
            }
        }
        System.out.println("下载完成");


        //创建容器，直接在创建的时候将文件复制进去，如果复制失败容器就不会启动，可以捕获到错误
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        //指定文件路径（Volumn）映射，作用是把本地的文件同步到容器中,可以让容器访问
        //也可以叫容器挂载目录
        HostConfig hostConfig = new HostConfig();
        hostConfig.setBinds(new Bind(userCodeParentPath,new Volume("/app")));
        //通过配置可以对容器进一些限制，如内存,cpu等
        hostConfig.withMemory(100 * 1000 * 1000L);   //限制容器的最大100MB
        hostConfig.withMemorySwap(0L);               //将内存交换设置为0，可以减少内存到硬盘的写入，一定情况下可以保证程序的稳定
        hostConfig.withCpuCount(1L);                 //单核
        hostConfig.withSecurityOpts(Arrays.asList("seccomp=seccomp-block-file.json"));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)   //限制网络资源，在创建容器时，设置网络配置为关闭
                .withReadonlyRootfs(true)            //限制用户不能向root根目录写文件
                .withAttachStderr(true)      //这三个withAttachStd 是把本地终端和docker建立连接 获取到输入，并且能在终端得到输出
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withTty(true)              //创建交互终端
                .exec();

        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        //启动容器
        dockerClient.startContainerCmd(containerId).exec();

        //交互式操作容器
        //docker exec strange_mcclintock java -cp /app Main 1 5

        //注意要把每个命令拆分成多个字符串，否则会被识别成字符串，而不是数组
        ArrayList<ExecuteMessage> executeMessageList = new ArrayList<>();
        for(String inputArgs : inputList){

            //创建stopWatch用于记录执行时间
            StopWatch stopWatch = new StopWatch();

            //创建命令
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[] {"java","-cp","/app","Main"},inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令: " + execCreateCmdResponse.toString());

            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0L;
            final boolean[] timeout = {true};


            //执行命令
            String execId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(){
                @Override
                public void onComplete() {
                    //如果执行完成，则表示没超时
                    timeout[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if(StreamType.STDERR.equals(streamType)){
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("错误结果: " + errorMessage[0]);
                    }else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果: " + message[0]);
                    }
                    super.onNext(frame);
                }
            };

            final long[] maxMemory = {0L};

            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback =statsCmd.exec(new ResultCallback<Statistics>() {

                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内容占用：" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(),maxMemory[0]);
                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });

            statsCmd.exec(statisticsResultCallback);

            try {
                stopWatch.start();   //记录代码执行前的时间
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);
                stopWatch.stop();   //记录代码执行后的时间
                time = stopWatch.getLastTaskTimeMillis(); //获取上次任务执行的毫秒数
                statsCmd.close();   //
            }catch (InterruptedException e){
                System.out.println("执行程序异常");
                throw new RuntimeException(e);
            }
            executeMessage.setTime(time);
            executeMessage.setMessage(message[0]);
            executeMessage.setMemory(maxMemory[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessageList.add(executeMessage);
        }
        return executeMessageList;
    }

}