// Copyright (C) 2019 Baidu Inc. All rights reserved.

package com.baidu.acu.pie.demo;

import com.baidu.acu.pie.client.AsrClient;
import com.baidu.acu.pie.client.AsrClientFactory;
import com.baidu.acu.pie.client.Consumer;
import com.baidu.acu.pie.exception.GlobalException;
import com.baidu.acu.pie.model.AsrConfig;
import com.baidu.acu.pie.model.AsrProduct;
import com.baidu.acu.pie.model.RecognitionResult;
import com.baidu.acu.pie.model.RequestMetaData;
import com.baidu.acu.pie.model.StreamContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import org.joda.time.DateTime;

/**
 * JavaDemo
 *
 * @author Cynric Shu (cynricshu@gmail.com)
 */
public class JavaBase64Demo {
    public static void main(String[] args) throws IOException {
        JavaBase64Demo javaDemo = new JavaBase64Demo();
        byte[] bytes = Files.readAllBytes(Paths.get("testaudio/16k.wav"));
        Base64.getEncoder().encode(bytes);
        String encode = Base64.getEncoder().encodeToString(bytes);
        String result = javaDemo.syncRecognition(encode);
        System.out.println(result);
    }

    private AsrConfig buildAsrConfig() {
        // asrConfig构造后就不可修改
        // 当使用ssl client时，需要配置字段sslUseFlag以及sslPath
        return AsrConfig.builder()
                .serverIp("127.0.0.1")
                .serverPort(8051)
                .appName("simpleDemo")
                .product(AsrProduct.SPEECH_SERVICE)
                .userName("username")
                .password("password")
//                .sslUseFlag(true)
//                .sslPath("ca.crt")
                .build();
    }

    private AsrClient createAsrClient() {
        return AsrClientFactory.buildClient(buildAsrConfig());
    }

    private RequestMetaData createRequestMeta() {
        RequestMetaData requestMetaData = new RequestMetaData();
        requestMetaData.setSendPackageRatio(1);
        requestMetaData.setSleepRatio(0.3);
        requestMetaData.setTimeoutMinutes(120);
        requestMetaData.setEnableFlushData(false);

        return requestMetaData;
    }

    public String syncRecognition(String base64) throws IOException {
        AsrClient asrClient = createAsrClient();

        RequestMetaData requestMetaData = createRequestMeta();
        final StringBuffer sb = new StringBuffer();

        StreamContext streamContext = asrClient.asyncRecognize(new Consumer<RecognitionResult>() {
            @Override
            public void accept(RecognitionResult it) {
                if (it != null && it.isCompleted()) {
                    sb.append(it.getResult());
                }
//                System.out.println(
//                        DateTime.now().toString() + "\t" + Thread.currentThread().getId() +
//                                " receive fragment: " + it);
            }
        }, requestMetaData);

        streamContext.enableCallback(new Consumer<GlobalException>() {
            @Override
            public void accept(GlobalException e) {
                if (e != null) {
                    System.out.println(e);
                }
            }
        });
        int bufferLengthInBytes = asrClient.getFragmentSize();
        byte[] data = Base64.getDecoder().decode(base64);
        int remainSize = data.length;
        while (remainSize > 0 && !streamContext.getFinishLatch().finished()) {
            byte[] part = new byte[bufferLengthInBytes];
            if (remainSize < bufferLengthInBytes) {
                part = new byte[remainSize];
            }
            System.arraycopy(data, data.length - remainSize, part, 0, part.length);
            remainSize = remainSize - part.length;
            streamContext.send(part);
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println(new DateTime().toString() + "\t" + Thread.currentThread().getId() + " send finish");
        streamContext.complete();

        // wait to ensure to receive the last response
        try {
            streamContext.getFinishLatch().await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}
