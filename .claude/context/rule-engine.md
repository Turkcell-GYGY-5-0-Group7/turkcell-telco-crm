# Active Rule Engine

## System Concept

This system is NOT a static documentation layer.

It is a **runtime decision support system for AI agents**.

---

# How It Works

Every agent must:

1. Query relevant rule domains
2. Resolve conflicts via rule priority
3. Apply constraints before generating output

---

# Rule Priority Order

1. Tech Lead Decisions (HIGHEST)
2. ADR Rules
3. Architecture Agent Rules
4. Platform Rules
5. Service-Level Rules
6. Local Context Rules

---

# Rule Query Model

Agents must interpret context via:

* architecture domain
* service domain
* event domain
* platform domain

---

# Rule Conflict Resolution

If rules conflict:

### Step 1

Check Tech Lead override

### Step 2

Check ADR priority

### Step 3

Check domain specificity

Most specific rule wins unless overridden

---

# Dynamic Behavior

Rules are:

* composable
* hierarchical
* override-capable
* version-aware

---

# Enforcement Rule

No agent can ignore rule engine output when generating artifacts.
