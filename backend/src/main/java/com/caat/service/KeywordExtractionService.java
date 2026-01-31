package com.caat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 关键词提取服务
 * 从内容中提取关键词并生成标签
 */
@Slf4j
@Service
public class KeywordExtractionService {
    
    // 中文停用词列表（简化版）
    private static final Set<String> CHINESE_STOP_WORDS = Set.of(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这"
    );
    
    // 英文停用词列表
    private static final Set<String> ENGLISH_STOP_WORDS = Set.of(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on", "with", "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we", "say", "her", "she", "or", "an", "will", "my", "one", "all", "would", "there", "their"
    );
    
    // 数字和特殊字符模式
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("^[^\\u4e00-\\u9fa5a-zA-Z0-9]+$");
    
    /**
     * 从文本中提取关键词
     * @param text 文本内容
     * @param maxKeywords 最大关键词数量
     * @return 关键词列表（按重要性排序）
     */
    public List<String> extractKeywords(String text, int maxKeywords) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        // 分词（简单实现：按空格、标点符号分割）
        List<String> words = tokenize(text);
        
        // 过滤停用词、数字、特殊字符
        List<String> filteredWords = words.stream()
                .filter(word -> word.length() > 1) // 至少2个字符
                .filter(word -> !isStopWord(word))
                .filter(word -> !NUMBER_PATTERN.matcher(word).matches())
                .filter(word -> !SPECIAL_CHAR_PATTERN.matcher(word).matches())
                .collect(Collectors.toList());
        
        // 计算词频
        Map<String, Integer> wordFreq = new HashMap<>();
        for (String word : filteredWords) {
            wordFreq.put(word, wordFreq.getOrDefault(word, 0) + 1);
        }
        
        // 按词频排序，取前 maxKeywords 个
        return wordFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(maxKeywords)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    /**
     * 从标题和正文中提取关键词
     * @param title 标题
     * @param body 正文
     * @param maxKeywords 最大关键词数量
     * @return 关键词列表
     */
    public List<String> extractKeywordsFromContent(String title, String body, int maxKeywords) {
        // 标题权重更高，重复一次
        String combinedText = "";
        if (title != null && !title.trim().isEmpty()) {
            combinedText = title + " " + title + " "; // 标题重复一次增加权重
        }
        if (body != null && !body.trim().isEmpty()) {
            combinedText += body;
        }
        
        return extractKeywords(combinedText, maxKeywords);
    }
    
    /**
     * 分词（简单实现）
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        
        // 处理中英文混合文本
        StringBuilder currentWord = new StringBuilder();
        
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c) || (c >= 0x4e00 && c <= 0x9fa5)) {
                // 中文字符、英文字母、数字
                currentWord.append(c);
            } else {
                // 遇到分隔符
                if (currentWord.length() > 0) {
                    tokens.add(currentWord.toString().toLowerCase());
                    currentWord.setLength(0);
                }
            }
        }
        
        // 添加最后一个词
        if (currentWord.length() > 0) {
            tokens.add(currentWord.toString().toLowerCase());
        }
        
        return tokens;
    }
    
    /**
     * 判断是否为停用词
     */
    private boolean isStopWord(String word) {
        String lowerWord = word.toLowerCase();
        return CHINESE_STOP_WORDS.contains(lowerWord) || 
               ENGLISH_STOP_WORDS.contains(lowerWord);
    }
}
