package com.lun.luncodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;

import java.util.List;


/**
 * @Description
 * @Date 2025/10/9
 */
public class DockerDemo {
    public static void main(String[] args) throws InterruptedException {
// 获取默认的 docker Client
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        String image = "nginx:latest";
//        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
//
//        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
//            @Override
//            public void onNext(PullResponseItem item) {
//// 打印更详细的信息，方便排错
//                System.out.println("下载镜像状态: " + item.getStatus());
//                if (item.getProgressDetail() != null) {
//                    System.out.println(" progressDetail: " + item.getProgressDetail().toString());
//                }
//                if (item.getErrorDetail() != null) {
//                    System.err.println(" errorDetail: " + item.getErrorDetail().getMessage());
//                }
//                super.onNext(item);
//            }
//
//            @Override
//            public void onError(Throwable throwable) {
//                System.err.println("拉取镜像出错: " + throwable.getMessage());
//                throwable.printStackTrace();
//            }
//
//            @Override
//            public void onComplete() {
//                System.out.println("拉取镜像流程完成回调");
//                super.onComplete();
//            }
//        };
//
//        pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
//        System.out.println("下载完成");
        //创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse = containerCmd
                .withCmd("echo","hello docker")
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        //查看容器
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        List<Container> containerList = listContainersCmd.withShowAll(true).exec();
        for(Container container:containerList){
            System.out.println(container);
        }

        //启动容器
        dockerClient.startContainerCmd(containerId).exec();

        //查看日志
        LogContainerResultCallback logContainerResultCallback = new LogContainerResultCallback(){
            @Override
            public void onNext(Frame item) {
                System.out.println(item.getStreamType());
                System.out.println("日志：" + new String(item.getPayload()));
                super.onNext(item);
            }
        };
        //阻塞等待日志输出
        dockerClient.logContainerCmd(containerId)
                .withStdErr(true)
                .withStdOut(true)
                .exec(logContainerResultCallback)
                .awaitCompletion();
//Docker 容器的日志输出是流式的（类似水流持续产生），而非一次性完成的操作。当调用exec(logContainerResultCallback)时，日志获取操作会异步开始：
//客户端会持续从容器接收日志数据
//每条日志通过onNext()回调方法处理
//而awaitCompletion()的作用是阻塞当前线程，直到日志流结束（比如容器停止输出日志、连接关闭或超时）。如果不阻塞，当前线程会继续执行后续代码，可能导致：
//日志还没完全接收就结束程序，丢失部分日志
//回调方法还在处理日志时，主线程已退出，导致日志处理不完整
        

//        //删除容器
//        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
//
//        //删除镜像
//        dockerClient.removeImageCmd(image).exec();
    }



}