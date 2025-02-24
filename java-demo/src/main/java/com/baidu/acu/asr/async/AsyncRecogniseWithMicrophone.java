package com.baidu.acu.asr.async;

import com.baidu.acu.asr.model.AsrArgs;
import com.baidu.acu.pie.client.AsrClient;
import com.baidu.acu.pie.client.AsrClientFactory;
import com.baidu.acu.pie.model.AsrConfig;
import com.baidu.acu.pie.model.RequestMetaData;
import com.baidu.acu.pie.model.StreamContext;
import com.baidu.acu.pie.util.JacksonUtil;
import org.joda.time.DateTime;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AsyncRecogniseWithMicrophone
 *
 * @author Xia Shuai(xiashuai01@baidu.com)
 * @literal create at 2021/3/3 2:32 下午
 */
public class AsyncRecogniseWithMicrophone {
    private static AsrArgs asrArgs;

    private static String appName = "microphone";     // 根据自己需求命名

    public static void main(String[] args) {
        // 带中间结果
        // java -jar java-demo-1.0-SNAPSHOT-jar-with-dependencies.jar -ip 127.0.0.1 -port 8051 -pid 1912 -username username -password password -enable-flush-data
        // 不带中间结果
        // java -jar java-demo-1.0-SNAPSHOT-jar-with-dependencies.jar -ip 127.0.0.1 -port 8051 -pid 1912 -username username -password password
        AsyncRecogniseWithMicrophone.asrArgs = AsrArgs.parse(args);
        asyncRecognitionWithMicrophone();
    }

    private static AsrClient createAsrClient() {
        // 创建调用asr服务的客户端
        // asrConfig构造后就不可修改
        return AsrClientFactory.buildClient(buildAsrConfig());
    }

    private static AsrConfig buildAsrConfig() {
        // asrConfig构造后就不可修改
        // 当使用ssl client时，需要配置字段sslUseFlag以及sslPath
        return AsrConfig.builder()
                .appName(appName)
                .serverIp(asrArgs.getIp())
                .serverPort(asrArgs.getPort())
                .product(AsrArgs.parseProduct(asrArgs.getProductId()))
                .userName(asrArgs.getUsername())
                .password(asrArgs.getPassword())
                .build();
    }

    private static RequestMetaData createRequestMeta() {
        RequestMetaData requestMetaData = new RequestMetaData();
        requestMetaData.setSendPackageRatio(1);
        requestMetaData.setSleepRatio(0);
        requestMetaData.setTimeoutMinutes(120);
        requestMetaData.setEnableFlushData(asrArgs.getEnableFlushData());
        // 随路信息根据需要设置
        Map<String, Object> extra_info = new HashMap<>();
        extra_info.put("demo", "java");
        extra_info.put("scene", "1234");
        requestMetaData.setExtraInfo(JacksonUtil.objectToString(extra_info));

        return requestMetaData;
    }

    /**
     * 识别麦克风音频流
     */
    public static void asyncRecognitionWithMicrophone() {
        AsrClient asrClient = createAsrClient();

        RequestMetaData requestMetaData = createRequestMeta();

        StreamContext streamContext = asrClient.asyncRecognize(it -> System.out.println(
                DateTime.now() + "\t" + Thread.currentThread().getId() +
                        " receive fragment: " + it), requestMetaData);

        streamContext.enableCallback(e -> {
            if (e != null) {
                e.printStackTrace();
            }
        });

        AsrConfig asrConfig = buildAsrConfig();

        TargetDataLine line = null;
        AudioFormat audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                asrConfig.getProduct().getSampleRate(),
                16,
                1,
                2,    // (sampleSizeBits / 8) * channels
                asrConfig.getProduct().getSampleRate(),
                false);

        DataLine.Info info = new DataLine.Info(
                TargetDataLine.class,
                audioFormat);

        if (!AudioSystem.isLineSupported(info)) {
            System.out.println("line not support");
            return;
        }

        FileOutputStream fop = null;
        try {
            if (!asrArgs.getAudioPath().equals("")) {
                File file = Paths.get(asrArgs.getAudioPath(), generateAudioName()).toFile();
                if (file.exists() || file.createNewFile()) {
                    fop = new FileOutputStream(file);
                }
            }
            line = (TargetDataLine) AudioSystem.getLine(info);

            line.open(audioFormat, line.getBufferSize());
            line.start();

            int bufferLengthInBytes = asrClient.getFragmentSize();
            byte[] data = new byte[bufferLengthInBytes];
            System.out.println("start to record");
            while ((line.read(data, 0, bufferLengthInBytes)) != -1L &&
                    !streamContext.getFinishLatch().finished()) {
                streamContext.send(data);
                if (fop != null) {
                    fop.write(data);
                    fop.flush();
                }
            }

            System.out.println(new DateTime() + "\t" + Thread.currentThread().getId() + " send finish");
            streamContext.complete();

            // wait to ensure to receive the last response
            streamContext.getFinishLatch().await();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (line != null) {
                line.close();
            }
            asrClient.shutdown();
            if (fop != null) {
                try {
                    fop.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("all task finished");
    }

    private static String generateAudioName() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String time = dateTimeFormatter.format(LocalDateTime.now());
        return time + UUID.randomUUID() + ".pcm";
    }
}
