package com.sky.test;


import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

@SpringBootTest
public class HttpClientTest {

//    通过httpclient发送get请求
    @Test
    public void testGet() throws IOException {
//        创建httpclient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
//        创建httpget对象
        HttpGet httpGet = new HttpGet("http://localhost:8080/user/shop/status");
//        执行请求
        CloseableHttpResponse response = httpClient.execute(httpGet);
//        获取响应状态码
        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("响应状态码：" + statusCode);
//        获取响应内容
        HttpEntity responseEntity = response.getEntity();
        String responseBody = EntityUtils.toString(responseEntity);
        System.out.println("响应内容：" + responseBody);
//        关闭响应和httpclient对象
        response.close();
        httpClient.close();
    }
//    通过httpclient发送post请求
    @Test
    public void testPost() throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://localhost:8080/admin/employee/login");
        StringEntity entity = new StringEntity("{\"username\":\"admin\",\"password\":\"123456\"}");
//        设置编码方式
        entity.setContentEncoding("UTF-8");
//        设置请求头
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
//        发送请求
        CloseableHttpResponse response = httpClient.execute((httpPost));
//        获取响应状态码
        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("响应状态码：" + statusCode);
//        获取响应内容
        HttpEntity responseEntity = response.getEntity();
        String responseBody = EntityUtils.toString(responseEntity);
        System.out.println("响应内容：" + responseBody);
//        关闭响应和httpclient对象
        response.close();
        httpClient.close();
    }

}
