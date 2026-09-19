package com.bienvenueblainville.agent;

/**
 * Everything the agent is allowed to do, enumerated.
 *
 * <p>An enum rather than a free-form string on purpose: whatever the model
 * emits has to land on one of these constants or the call is rejected before
 * anything runs. The surface is deliberately narrow — the collection schedule
 * and public notices only.
 *
 * <p>Sorting-guide entries are <em>not</em> here, and that is a decision, not
 * an omission. Creating one requires French, English and Chinese names,
 * instructions and search keywords; a model asked for those would be inventing
 * municipal facts, which is exactly what the sorting assistant's whole design
 * exists to prevent. Schedule rows and notices are different: their content
 * comes from the administrator's own instruction.
 */
public enum AgentToolName {
    list_collections(Kind.READ),
    list_notices(Kind.READ),
    create_collection(Kind.WRITE),
    update_collection(Kind.WRITE),
    delete_collection(Kind.WRITE),
    create_notice(Kind.WRITE),
    update_notice(Kind.WRITE),
    delete_notice(Kind.WRITE);

    public enum Kind { READ, WRITE }

    private final Kind kind;

    AgentToolName(Kind kind) {
        this.kind = kind;
    }

    /**
     * Reads run during planning so the model can ground itself in the real
     * schedule; writes are only ever recorded into a plan and wait for a human.
     */
    public boolean isWrite() {
        return kind == Kind.WRITE;
    }

    static AgentToolName from(String name) {
        for (AgentToolName tool : values()) {
            if (tool.name().equals(name)) {
                return tool;
            }
        }
        throw new IllegalArgumentException("Unknown tool: " + name);
    }
}
