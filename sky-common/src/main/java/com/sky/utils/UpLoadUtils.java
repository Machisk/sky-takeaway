package com.sky.utils;

import com.sky.properties.UpLoadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Component
public class UpLoadUtils {

    private static final Logger log = LoggerFactory.getLogger(UpLoadUtils.class);
    @Autowired
    private UpLoadProperties upLoadProperties;

    public String upload(MultipartFile image) throws IOException {
        //获取参数
        int port = upLoadProperties.getPort();
        String address = upLoadProperties.getAddress();
        //文件名
        String locate = image.getOriginalFilename();
        String filename = UUID.randomUUID().toString()+locate.substring(locate.lastIndexOf("."));
        log.info("文件名称：{}",filename);

        //首次需生成目录
        File folder = new File("UploadFile");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        //上传文件到指定路径
        image.transferTo(new File(folder.getCanonicalPath()+"/"+filename));

        //url
        String url = address + ":" + port + "/images/" + filename;
        return url;
    }
}