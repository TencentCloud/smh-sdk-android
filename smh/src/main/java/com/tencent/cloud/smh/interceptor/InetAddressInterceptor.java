package com.tencent.cloud.smh.interceptor;

import com.tencent.qcloud.core.http.QCloudHttpClient;
import com.tencent.qcloud.core.logger.QCloudLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.connection.RealConnection;
import okhttp3.internal.http.RealInterceptorChain;
/**
 * 打印服务器host和对应的IP
 * <p>
 * Created by jordanqin on 2022/6/28 11:58 上午
 * Copyright 2010-2022 Tencent Cloud. All Rights Reserved.
 */
public class InetAddressInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        try {
            if (chain instanceof RealInterceptorChain) {
                Connection connection = chain.connection();
                if (connection instanceof RealConnection) {
                    RealConnection realConnection = (RealConnection) connection;
                    Socket socket = realConnection.socket();
                    InetAddress inetAddress = socket.getInetAddress();
                    if(inetAddress != null){
                        QCloudLogger.i(
                                QCloudHttpClient.HTTP_LOG_TAG,
                                String.format("InetAddress: %s", inetAddress.toString())
                        );
                    }
                }
            }
        } catch (Exception e) {
            QCloudLogger.d("HttpMetricsInterceptor", e.getMessage());
        }
        return chain.proceed(request);
    }
}