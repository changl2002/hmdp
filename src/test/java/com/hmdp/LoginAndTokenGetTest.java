package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class LoginAndTokenGetTest {
    @Resource
    private UserMapper userMapper;
    @Test
    public void mothod() throws IOException {
        String string = HttpClientUtil.doPost("http://localhost:8080/api/user/code?phone=18451873429", null);
        System.out.println(string);
    }

    @Test
    public void testLogin() throws IOException {
        List<String> phones = userMapper.getPhones();
        String URL = "http://localhost:8080/api/user/login";
        System.out.println(phones.size());
        BufferedWriter bw=new BufferedWriter(new FileWriter("tokens.txt"));
        // System.out.println(phones);
        for (int i = 0; i < phones.size(); i++) {
            Map<String,String> map=new HashMap<>();
            map.put("phone",phones.get(i));
            String tokenResult = HttpClientUtil.doPost4Json(URL,map);
            Result bean = JSONUtil.toBean(tokenResult, Result.class);
            String token = (String) bean.getData();
            System.out.println(token);
            bw.write(token);
            bw.newLine();
        }
        bw.close();
    }
}
