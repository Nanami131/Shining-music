package org.L2.common.minio.service;


import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FileNameGenerateService {
    // 静态字符集，复用
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static String code(int length) {
        // 效率更高
        StringBuilder randomString = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomIndex = ThreadLocalRandom.current().nextInt(CHARACTERS.length());
            randomString.append(CHARACTERS.charAt(randomIndex));
        }
        return randomString.toString();
    }
    public static String defineNamePath(String originalFilename,String prefix,long id,int length) {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        String uniqueName = timestamp + code(length) + id + originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = prefix + uniqueName;
        return fileName;
    }
}
