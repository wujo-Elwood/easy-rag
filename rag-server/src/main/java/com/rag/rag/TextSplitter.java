package com.rag.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 自适应文本分块器
 * 优先按段落（\n\n）分割，超长段落再按句子切分
 * 保留每个 chunk 的来源信息（文件名 + 段落序号）
 */
@Component
public class TextSplitter {

    @Value("${rag.chunk-size}")
    private int chunkSize;

    @Value("${rag.chunk-overlap}")
    private int chunkOverlap;

    /**
     * 按自适应策略分割文本
     * 优先按段落边界切分，超长段落再按句子切分
     */
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        // 第1步：按段落（双换行）分割
        String[] paragraphs = text.split("\\n\\n+");
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) {
                continue;
            }
            // 如果当前 buffer 加上新段落不超限，合并
            if (buffer.length() + paragraph.length() + 2 <= chunkSize) {
                if (buffer.length() > 0) {
                    buffer.append("\n\n");
                }
                buffer.append(paragraph);
            } else {
                // buffer 已有内容时先保存
                if (buffer.length() > 0) {
                    chunks.add(buffer.toString());
                    buffer = new StringBuilder();
                }
                // 段落本身超长，按句子切分
                if (paragraph.length() > chunkSize) {
                    List<String> subChunks = splitBySentence(paragraph);
                    chunks.addAll(subChunks);
                } else {
                    buffer.append(paragraph);
                }
            }
        }
        // 最后剩余的内容
        if (buffer.length() > 0) {
            chunks.add(buffer.toString());
        }
        return chunks;
    }

    /**
     * 按句子切分超长段落
     * 先按句号/问号/感叹号分句，再合并到 chunkSize 以内
     */
    private List<String> splitBySentence(String text) {
        List<String> chunks = new ArrayList<>();
        // 按中英文句号、问号、感叹号、分号分句
        String[] sentences = text.split("(?<=[。！？；.!?;])\\s*");
        StringBuilder buffer = new StringBuilder();
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) {
                continue; // 跳过空句子
            }
            // 判断能否合并到当前块
            if (buffer.length() + sentence.length() + 1 <= chunkSize) {
                // 可以合并
                if (buffer.length() > 0) {
                    buffer.append(" ");// 句子之间加空格（不是换行）
                }
                buffer.append(sentence);
            } else {
                // 不能合并，先保存当前块
                if (buffer.length() > 0) {
                    chunks.add(buffer.toString());
                }
                // 单句超长时按固定大小切割
                if (sentence.length() > chunkSize) {
                    // 单句超长：按固定大小暴力切割
                    chunks.addAll(splitFixed(sentence));
                    buffer = new StringBuilder();
                } else {
                    // 句子不超长，作为新块的开始
                    buffer = new StringBuilder(sentence);
                }
            }
        }
        if (buffer.length() > 0) {
            chunks.add(buffer.toString());
        }
        return chunks;
    }

    /**
     * 按固定大小切分（最后的兜底策略）
     */
    private List<String> splitFixed(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - chunkOverlap;
        }
        return chunks;
    }
}
