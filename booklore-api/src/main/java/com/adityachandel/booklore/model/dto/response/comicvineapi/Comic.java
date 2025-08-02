package com.adityachandel.booklore.model.dto.response.comicvineapi;
import java.time.LocalDate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        
        property = "resource_type",
        defaultImpl = Volume.class
)

@JsonSubTypes({
        @JsonSubTypes.Type(value = Volume.class, name = "volume"),
        @JsonSubTypes.Type(value = Issue.class, name = "issue")
}) 

public abstract class Comic {
        private int id;
        protected String name;
        protected ComicVineImage image;
        private String description;

        @JsonProperty("concept_credits")
        private List<ComicvineItem> conceptCredits;


        @JsonProperty("person_credits")
        private List<ComicvineItem> personCredits;




        
        @JsonProperty("resource_type")
        private String resourceType;
        

        public abstract String getDisplayName();

        public abstract String getComicId();

        public abstract LocalDate getDate();

        public Set<String> getAuthors() {
            Set<String> authors = new HashSet<>();
            if (personCredits != null) {
            for (ComicvineItem person : personCredits) {
                authors.add(person.name);
            }
            }
            return authors;
        }

        public String getImageUrl() {
            if (image == null) {
                return null;
            }
            return image.getOriginalUrl() != null ? image.getOriginalUrl() : image.getThumbUrl();
        }


    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComicVineImage {
        @JsonProperty("icon_url")
        private String iconUrl;
        
        @JsonProperty("medium_url")
        private String mediumUrl;
        
        @JsonProperty("screen_url")
        private String screenUrl;
        
        @JsonProperty("screen_large_url")
        private String screenLargeUrl;
        
        @JsonProperty("small_url")
        private String smallUrl;
        
        @JsonProperty("super_url")
        private String superUrl;
        
        @JsonProperty("thumb_url")
        private String thumbUrl;
        
        @JsonProperty("tiny_url")
        private String tinyUrl;
        
        @JsonProperty("original_url")
        private String originalUrl;
        
        @JsonProperty("image_tags")
        private String imageTags;
    }
     

      @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComicvineItem {
        @JsonProperty("api_detail_url")
        private String apiDetailUrl;
        
        private int id;
        private String name;
    }


    }

    