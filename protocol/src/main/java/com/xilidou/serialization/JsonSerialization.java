package com.xilidou.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xilidou.api.Serialization;

import java.io.IOException;

/**
 * @author Zhengxin
 */
public class JsonSerialization implements Serialization {

    private ObjectMapper objectMapper;

    public JsonSerialization(){
        this.objectMapper = new ObjectMapper();
    }


    @Override
    public <T> byte[] serialize(T obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public <T> T deSerialize(byte[] data, Class<T> clz) {
        try {
            return objectMapper.readValue(data,clz);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
