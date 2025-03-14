package com.tencent.supersonic.common.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatApp {
    private String key;
    private String name;
    private String description;
    private String prompt;
    private boolean enable;
    private Integer chatModelId;
    @JsonIgnore
    private ChatModelConfig chatModelConfig;
}
