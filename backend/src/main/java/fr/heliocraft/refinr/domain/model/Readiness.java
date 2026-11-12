package fr.heliocraft.refinr.domain.model;

/** Verdict de preparation d'une story pour un agent de code. */
public record Readiness(boolean aiReady, String aiReadyReason) {}
