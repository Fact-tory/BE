package com.commonground.be.domain.search.dto.response;

import lombok.Getter;

import java.util.List;

@Getter
public class AutocompleteResponse {
    
    private final String query;
    private final List<SuggestionItem> suggestions;
    private final int totalSuggestions;

    public AutocompleteResponse(String query, List<SuggestionItem> suggestions) {
        this.query = query;
        this.suggestions = suggestions;
        this.totalSuggestions = suggestions.size();
    }

    @Getter
    public static class SuggestionItem {
        private final String text;
        private final String type;
        private final int frequency;
        private final double score;

        public SuggestionItem(String text, String type, int frequency, double score) {
            this.text = text;
            this.type = type;
            this.frequency = frequency;
            this.score = score;
        }
    }
}