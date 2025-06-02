package com.sav.springchatbot.utils;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ChatToRedis {
    private Long userId;
    private String msgUser;
    private String msgBot;
    private String timeUser;
    private String timeBot;
}
