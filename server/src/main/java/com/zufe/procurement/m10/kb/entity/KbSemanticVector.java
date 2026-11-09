package com.zufe.procurement.m10.kb.entity;

import lombok.Data;
@Data
public class KbSemanticVector {
    private Long chunkId;
    private String configFingerprint;
    private String model;
    private Integer dimensions;
    private String preprocessingVersion;
    private String contentHash;
    private String vectorJson;
}
