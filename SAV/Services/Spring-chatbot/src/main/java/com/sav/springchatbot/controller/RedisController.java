package com.sav.springchatbot.controller;

import com.sav.springchatbot.services.ChatbotService;
import com.sav.springchatbot.services.RedisService;
import com.sav.springchatbot.utils.ChatToRedis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
//@CrossOrigin(origins = "http://localhost:4200") // Autoriser les requêtes de localhost:4200
public class RedisController {

    private final RedisService redisService;

    @Autowired
    ChatbotService chatService;

    public RedisController(RedisService redisService) {
        this.redisService = redisService;
    }

    @PostMapping("/save")
    public String saveData(@RequestParam String key, @RequestParam String value) {
        redisService.saveData(key, value);
        return "Donnée enregistrée !";
    }

    @GetMapping("/get")
    public String getData(@RequestParam String key) {
        return redisService.getData(key);
    }

    @GetMapping("/chats/{userId}")
    public ResponseEntity<List<ChatToRedis>> getChats(@PathVariable Long userId) {
        List<ChatToRedis> chats = chatService.getChatsForUser(userId);
        return ResponseEntity.ok(chats);
    }

}