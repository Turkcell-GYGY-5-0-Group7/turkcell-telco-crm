# Lessons

Self-improvement log. After any correction from the user, append the pattern here as a rule that
prevents the same mistake. Review at session start. Keep entries short and actionable.

Format:

```
## YYYY-MM-DD - <short title>
- Mistake: <what went wrong>
- Rule: <what to do instead, every time>
```

---

## 2026-06-22 - docs/tasks is the single status source of truth
- Mistake: status lived in two unreconciled places (.claude/roadmap vs docs/tasks).
- Rule: `docs/tasks/` is authoritative for delivery status and program structure (epics/phases live
  in `docs/tasks/STATUS.md`). Update the owning sprint README and `STATUS.md` together. The separate
  `.claude/roadmap` tracker was removed to eliminate the dual-source-of-truth complexity.
