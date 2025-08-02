package com.adityachandel.booklore.model.dto.response.comicvineapi;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Volume extends Comic{
    @JsonProperty("api_detail_url")
    private String apiDetailUrl;

    private String description;

    @JsonProperty("publisher")
    private ComicvineItem publisher;

    @JsonProperty("site_detail_url")
    private String siteDetailUrl;

    @JsonProperty("start_year")
    private String startYear;

    public String getPublisherName() {
        return publisher != null ? publisher.getName() : null;
    }


    @Override
    public String getDisplayName(){
        if(name==null){
            return "Unknown Comic";
        }
        else{
            return name;
        }
    }

    @Override   
    public String getComicId() {
        return "4500-" + String.valueOf(getId());
    }

    @Override
    public LocalDate getDate() {
        if (startYear == null || startYear.isEmpty()) return null;
        try {
            return LocalDate.parse(startYear, DateTimeFormatter.ofPattern("yyyy"));
        } catch (Exception e) {
            return null;
        }
    }
}

