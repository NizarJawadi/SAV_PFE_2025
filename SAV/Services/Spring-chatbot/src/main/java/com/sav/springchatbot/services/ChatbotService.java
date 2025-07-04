package com.sav.springchatbot.services;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sav.springchatbot.DTO.ProduitDTO;
import com.sav.springchatbot.DTO.ReclamationDTO;
import com.sav.springchatbot.DTO.StatutReclamation;
import com.sav.springchatbot.entity.Conversation;
import com.sav.springchatbot.feign.GuideFeignClient;
import com.sav.springchatbot.feign.ProduitFeignClient;
import com.sav.springchatbot.feign.ReclamationFeignClient;
import com.sav.springchatbot.repository.ConversationRepository;
import com.sav.springchatbot.utils.ChatToRedis;
import com.sav.springchatbot.utils.QuestionType;
import com.sav.springchatbot.services.DecisionEngine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatbotService {


    @Autowired
    private ObjectMapper objectMapper;


    private final WebClient webClient;
    private final ConversationRepository conversationRepository;
    private final StringRedisTemplate redisTemplate;
    private final DecisionEngine decisionEngine;
    private final ReclamationFeignClient reclamationClient;
    private final ProduitFeignClient produitClient;
    private final GuideFeignClient guideClient;

    public ChatbotService(WebClient.Builder webClientBuilder,
                          ConversationRepository conversationRepository,
                          StringRedisTemplate redisTemplate,
                          DecisionEngine decisionEngine,
                          ReclamationFeignClient reclamationClient,
                          ProduitFeignClient produitClient,
                          GuideFeignClient guideClient) {
        this.webClient = webClientBuilder.baseUrl("http://localhost:11434").build();
        this.conversationRepository = conversationRepository;
        this.redisTemplate = redisTemplate;
        this.decisionEngine = decisionEngine;
        this.reclamationClient = reclamationClient;
        this.produitClient = produitClient;
        this.guideClient = guideClient;
    }

    public String getResponse(Long userId, String question) throws JsonProcessingException {
        QuestionType type = decisionEngine.analyse(question);
        String response;

        switch (type) {
            case TYPE_RECLAMATION:
                System.out.println(type.name());
                response = handleReclamationQuestion(userId, question);
                break;
            case TYPE_GUIDE:
                System.out.println(type.name());

                response = handleGuideQuestion(userId, question);
                break;
            default:
                System.out.println("reponse ollama ");

                response = askOllama(question);
        }


        // Stocker dans Redis (nouveau format structuré)
        ChatToRedis chat = new ChatToRedis(
                userId,
                question,
                response,
                LocalDateTime.now().toString(),
                LocalDateTime.now().toString()
        );

        String redisKey = "chat:" + userId + ":" + System.currentTimeMillis();

        String jsonChat = objectMapper.writeValueAsString(chat); // sérialisation

        // Stocker l'historique
        //redisTemplate.opsForValue().set("chat:" + userId, question + " -> " + response);
        redisTemplate.opsForValue().set(redisKey, jsonChat);

        conversationRepository.save(new Conversation(null, userId, question, response, LocalDateTime.now()));

        return response;
    }

    private String handleReclamationQuestion(Long userId, String question) {
        String produitNomExtrait = extractProductNameFromQuestion(question).toLowerCase();

        List<ReclamationDTO> reclamations = reclamationClient.getReclamationsByUser(userId);
        System.out.println(reclamations);
        for (ReclamationDTO r : reclamations) {

            ProduitDTO produit = produitClient.getProduitById(r.getProduitId());
            String nomProduit = produit.getNom().toLowerCase();

            if (nomProduit.contains(produitNomExtrait) || produitNomExtrait.contains(nomProduit)) {
                if (r.getStatut() != null && r.getStatut() == StatutReclamation.EN_COURS) {
                    return "Oui, votre réclamation sur " + produit.getNom() + " est en cours de traitement.";
                } else if (r.getStatut() != null) {
                    return "Votre réclamation est actuellement : " + r.getStatut();
                } else {
                    return "Statut de réclamation non disponible.";
                }
            }
        }

        return "Je n'ai trouvé aucune réclamation correspondant à votre demande.";
    }


    private String handleGuideQuestion(Long userId, String question) {
        try {
            String produitNom = extractProductNameFromQuestion(question);
            ProduitDTO produit = produitClient.getProduitByNom(produitNom);

            // On suppose que le nom du fichier guide PDF est stocké dans ProduitDTO
            String filename = produit.getGuideTechniqueUrl(); // Ex: "guide-iPhone.pdf"
            String pdfPath = "D:/pdf/" + filename;

            String guideText = extractTextFromPdf(pdfPath);

            // Construction du prompt pour LLM
            String prompt = "Voici le guide technique :\n" + guideText + "\n\nRéponds à cette question en resume dans cinq lignes : " + question;

            return askOllama(prompt);

        } catch (Exception e) {
            e.printStackTrace();
            return "Une erreur est survenue lors du traitement du guide produit.";
        }
    }
    private String extractTextFromPdf(String pdfFilePath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(pdfFilePath))) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            //System.out.println(pdfStripper.getText(document));
            return pdfStripper.getText(document);
        }
    }



    private String extractProductNameFromQuestion(String question) {
        try {
            FrenchTokenizer tokenizer = new FrenchTokenizer("static/models/opennlp-fr-ud-gsd-tokens-1.2-2.5.0.bin");
            String[] tokens = tokenizer.tokenize(question.toLowerCase());

            List<ProduitDTO> produits = produitClient.getAllProduits(); // Suppose que tu as cette méthode

            for (ProduitDTO produit : produits) {
                String nomProduit = produit.getNom().toLowerCase();

                // Vérifie si tous les mots du nom du produit sont présents dans la question
                boolean match = true;
                for (String mot : nomProduit.split("\\s+")) {
                    if (!question.toLowerCase().contains(mot)) {
                        match = false;
                        break;
                    }
                }

                // Si on trouve un match partiel ou total
                if (match || containsAny(tokens, nomProduit.split("\\s+"))) {
                    return produit.getNom();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "produit"; // fallback
    }

    private boolean containsAny(String[] tokens, String[] targetWords) {
        for (String target : targetWords) {
            for (String token : tokens) {
                if (token.equalsIgnoreCase(target)) {
                    return true;
                }
            }
        }
        return false;
    }


    private String askOllama(String question) {
        try {
            String model = "llama2-fast";
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "prompt", " Réponds toujours en français , tu endosses le rôle d'une application de SAV. Réponds uniquement aux questions liées aux produits et en français , réclamations ou assistance technique. Ma question : " + question,
                    "temperature", 0.2  // Valeur modifiable selon ton besoin
            );

            String rawResponse = webClient.post()
                    .uri("/api/generate")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            System.out.println(rawResponse);
            return formatResponse(rawResponse);
        } catch (Exception e) {
            e.printStackTrace();
            return "Une erreur est survenue lors de la génération de la réponse.";
        }
    }


    private String formatResponse(String response) {
        StringBuilder formattedResponse = new StringBuilder();
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    try {
                        JSONObject json = new JSONObject(line);
                        if (json.has("response")) {
                            String part = json.getString("response");
                            formattedResponse.append(part);
                        }
                    } catch (JSONException e) {
                        // Ignorer les lignes qui ne sont pas du JSON valide
                        continue;
                    }
                }
            }

            // Nettoyage final du texte complet
            String finalText = formattedResponse.toString()
                    .replaceAll("\\s+", " ")  // Remplace les espaces multiples par un seul
                    .replaceAll("\\s([.,!?;:])", "$1")  // Supprime les espaces avant ponctuation
                    .trim();

            return finalText;
        } catch (Exception e) {
            e.printStackTrace();
            return "Désolé, une erreur est survenue lors du traitement de la réponse.";
        }
    }




    public String getSessionHistory(String userId) {
        return redisTemplate.opsForValue().get("chat:" + userId);
    }


    public List<ChatToRedis> getChatsForUser(Long userId) {
        Set<String> keys = redisTemplate.keys("chat:" + userId + ":*");
        List<ChatToRedis> chats = new ArrayList<>();

        if (keys != null) {
            for (String key : keys) {
                String json = redisTemplate.opsForValue().get(key);
                if (json != null) {
                    try {
                        ChatToRedis chat = objectMapper.readValue(json, ChatToRedis.class);
                        // Vérifier la date timeUser
                        LocalDateTime timeUser = LocalDateTime.parse(chat.getTimeUser());
                        LocalDateTime now = LocalDateTime.now();

                        if (timeUser.isBefore(now.minusHours(48))) {
                            // Supprimer la clé Redis car chat dépassé 48h
                            redisTemplate.delete(key);
                        } else {
                            chats.add(chat);
                        }
                    } catch
                    (JsonProcessingException e) {
                        // log or handle malformed JSON
                    } catch (Exception e) {
                        // Gérer d'autres exceptions (ex: parse erreur)
                        e.printStackTrace();
                    }
                }
            }
        }
        chats.sort(Comparator.comparing(ChatToRedis::getTimeUser));
        return chats;
    }


}