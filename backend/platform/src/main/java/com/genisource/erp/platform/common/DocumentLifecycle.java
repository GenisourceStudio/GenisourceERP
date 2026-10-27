package com.genisource.erp.platform.common;

/**
 * The life of a document that somebody submits, somebody approves and somebody
 * carries out.
 *
 * <p>Approval is not a second status field. A document that has been approved and is
 * half delivered is both things at once, and two fields would have to be kept in step
 * by hand. Instead the approval leaves a trace — who, when, what they said — and the
 * status carries on describing where the document has got to.
 *
 * <p>Not every value applies to every document: a requisition is never shipped and a
 * goods receipt is never partly delivered. What applies where is written on the
 * document types that use this enum.
 */
public enum DocumentLifecycle {

    /** Being typed in. Editable, invisible to anybody else. */
    DRAFT,
    /** Handed over for approval. No longer editable. */
    SUBMITTED,
    /** Approved and waiting to be carried out. */
    APPROVED,
    /** Turned down. Terminal: post a new document instead of reusing this one. */
    REJECTED,
    /** Carried out in part: some lines or some quantity still open. */
    PARTIAL,
    /** Carried out in full. */
    COMPLETED,
    /** Deliberately shut although not everything happened. */
    CLOSED,
    /** Withdrawn before anything happened. */
    CANCELLED,
    /** Posted: it has moved stock or money and is now frozen. */
    POSTED,
    /** Undone by a reversal, which is a separate document. */
    REVERSED;

    /** Whether the header and its lines may still be edited. */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /** Whether anything can still happen to it. */
    public boolean isOpen() {
        return this == DRAFT || this == SUBMITTED || this == APPROVED || this == PARTIAL;
    }

    /** Whether it has reached an end and cannot move again. */
    public boolean isTerminal() {
        return this == REJECTED || this == COMPLETED || this == CLOSED
                || this == CANCELLED || this == REVERSED;
    }

    /** Whether it has moved stock or money and is therefore frozen. */
    public boolean isPosted() {
        return this == POSTED;
    }
}
