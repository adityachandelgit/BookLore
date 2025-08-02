package com.adityachandel.booklore.model.dto.response.comicvineapi;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Issue extends Comic {
    @JsonProperty("cover_date")
    private String coverDate;

    @JsonProperty("volume")
    private ComicvineItem volume;

    @JsonProperty("issue_number")
    private int issueNumber;


    @Override   
    public String getComicId() {
        return "4000-" + String.valueOf(getId());
    }

    @Override
    public String getDisplayName() {
        if(name ==null){
            if(volume != null){
                return volume.getName() + " " + "Issue #" + String.valueOf(issueNumber);

            }
            else{
                return "Unknown Comic";
            }


        }
        return name;
    }

    @Override
    public LocalDate getDate() {
        if (coverDate == null || coverDate.isEmpty()) return null;
        try {
            return LocalDate.parse(coverDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return null;
        }
    }
}
