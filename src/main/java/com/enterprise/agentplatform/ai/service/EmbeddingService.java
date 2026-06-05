package com.enterprise.agentplatform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private final Optional<EmbeddingModel> embeddingModel;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public EmbeddingService(
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            ObjectMapper objectMapper,
            @Value("${app.ai.embedding.enabled:false}") boolean enabled
    ) {
        this.embeddingModel = Optional.ofNullable(embeddingModelProvider.getIfAvailable());
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public Optional<List<Double>> embed(String text) {
        if (!enabled || embeddingModel.isEmpty() || text == null || text.isBlank()) {
            return Optional.empty();
        }
        float[] vector = embeddingModel.get().embed(text);
        return Optional.of(toDoubleList(vector));
    }

    public Optional<String> embedAsJson(String text) {
        return embed(text).flatMap(this::toJson);
    }

    public Optional<List<Double>> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            Double[] values = objectMapper.readValue(json, Double[].class);
            return Optional.of(Arrays.asList(values));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    public double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || left.size() != right.size()) {
            return 0.0d;
        }
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < left.size(); i++) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private List<Double> toDoubleList(float[] vector) {
        return java.util.stream.IntStream.range(0, vector.length)
                .mapToObj(index -> (double) vector[index])
                .toList();
    }

    private Optional<String> toJson(List<Double> vector) {
        try {
            return Optional.of(objectMapper.writeValueAsString(vector));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }
}
