package com.zufe.procurement.m7.assistant.task;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Task-specific contracts; the existing chat and knowledge answer modes stay compatible. */
public final class TaskContracts {
    private TaskContracts() {}

    public record Evidence(int id, String kind, String title, String text, String path,
                           Long sourceId, String version) {}
    public record Check(String code, String status, String title, String detail, List<Integer> evidenceIds) {}
    public record Observation(String text, List<Integer> evidenceIds) {}
    public record ReviewRequest(String question, String focus) {}

    @Data public static class ReviewResult {
        private String runId;
        private Long orderId;
        private String orderNo;
        private String question;
        private String focus;
        private String asOf;
        private String snapshotId;
        private String mode = "facts_only";
        private String degradedReason;
        private String model;
        private Integer totalTokens;
        private long elapsedMs;
        private String promptVersion = "order-review-v1";
        private List<Evidence> evidence = new ArrayList<>();
        private List<Check> checks = new ArrayList<>();
        private List<Observation> observations = new ArrayList<>();
        private List<String> missingEvidence = new ArrayList<>();
        private List<String> stages = new ArrayList<>();
    }

    public record DraftRequest(String text) {}
    public record MaterialOption(Long id, String no, String name, String spec, String unit) {}
    public record OwnerOption(Long id, int type, String no, String name) {}
    public record DraftSelection(int line, Long materialId, BigDecimal quantity, String requireDate,
                                 int ownerType, Long ownerId, boolean unitConfirmed) {}
    public record DraftValidationRequest(String runId, List<DraftSelection> items) {}
    public record ValidatedLine(Long materialId, String materialName, String unit, BigDecimal quantity,
                                String requireDate, int ownerType, Long ownerId) {}
    public record DraftValidation(String runId, List<ValidatedLine> items, String accessFingerprint) {}

    @Data public static class DraftLine {
        private int line;
        private String source;
        private String materialText;
        private BigDecimal quantity;
        private String unit;
        private String requireDate;
        private String ownerText;
        private Long materialId;
        private Integer ownerType;
        private Long ownerId;
        private List<MaterialOption> materialCandidates = new ArrayList<>();
        private List<OwnerOption> ownerCandidates = new ArrayList<>();
        private List<String> issues = new ArrayList<>();
    }

    @Data public static class DraftResult {
        private String runId;
        private String text;
        private String today;
        private String mode;
        private String degradedReason;
        private String model;
        private Integer totalTokens;
        private long elapsedMs;
        private String promptVersion = "request-draft-v1";
        private List<DraftLine> items = new ArrayList<>();
        private List<MaterialOption> materials = new ArrayList<>();
        private List<OwnerOption> owners = new ArrayList<>();
        private List<String> notices = new ArrayList<>();
    }
}
