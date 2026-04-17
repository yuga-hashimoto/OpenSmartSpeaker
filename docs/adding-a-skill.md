# Adding a skill — step-by-step

Skills are markdown files that extend the agent with a reusable
capability. This page walks through authoring one, either as a
bundled skill (ships with the app) or an installable skill
(downloaded at runtime). Reference: [skills.md](skills.md) for the
full format and existing bundled-skill catalogue.

## 1. Pick the moment

A skill is a moment, not a Swiss-army knife. Before writing, answer:

- **What utterance triggers it?** Write 5 concrete variants in the
  language(s) you target.
- **What happens in the first 10 seconds after the trigger?** Skills
  that stretch past ~10 s usually need the LLM's full tool-chaining,
  which the base system already provides. The skill is only worth
  adding if its *voice* (tone, brevity, anti-goals) adds value.
- **What does the skill explicitly NOT do?** Anti-goals are as
  important as goals — they stop the LLM from drifting.

Example: `arrival-home` is the "user just walked in" moment. It does
NOT do music, weather briefings, or scene cascades — those belong to
other skills and would be invasive defaults.

## 2. Write `SKILL.md`

```md
---
name: skill-slug
description: One-sentence description, visible in Settings → Skills and in the `<available_skills>` prompt injection.
---
# Skill Title

Trigger on "phrase 1", "phrase 2", "日本語フレーズ". Distinct from
related skills X / Y because REASON.

## Default flow

1. **Step 1** — what tool fires first and why.
2. **Step 2** — …
3. **Reply shape** — what the assistant says. Be specific: "Two
   sentences max" beats "be brief".

## What this skill does NOT do

- Concrete anti-goal 1.
- Concrete anti-goal 2.

## Follow-ups

- What if the user says "stop"?
- What if they correct you?
```

Rules for the body:
- Keep it under ~300 tokens. The full body is injected into the
  system prompt whenever a trigger fires.
- Reference existing tools by exact name. Check
  [tools.md](tools.md).
- Don't duplicate prompt-level guardrails that the global system
  prompt already enforces.

## 3. Test manually

There is no automated test for skill behaviour — the real test is
speaking the trigger phrases on a real device and seeing whether the
assistant does what you intended. Before committing:

1. `./gradlew assembleDebug` (smoke only — asset loaders don't have
   a dedicated unit test).
2. Sideload the APK.
3. Say each trigger variant. Verify the flow matches the SKILL.md.
4. Say one close-but-wrong utterance to check the skill doesn't fire
   when it shouldn't.
5. Say "stop" / "解除して" at each step to verify follow-ups.

## 4. Ship paths

### 4a. Bundled (ships with app)

1. Drop your `SKILL.md` into `app/src/main/assets/skills/<slug>/`.
2. Add a row to the **Bundled skills** table in
   [skills.md](skills.md).
3. `AssetSkillLoader` picks it up on next launch — no code changes
   needed.

### 4b. Installable (user-downloadable)

1. Host `<slug>/SKILL.md` at a URL (GitHub raw, your own server,
   Gist — anything that serves `text/markdown` or `text/plain`).
2. User runs:
   ```
   install_skill_from_url { url: "https://..." }
   ```
   via voice or the Settings → Skills → Install from URL sheet.
3. `SkillInstaller` downloads to `filesDir/skills/<slug>/`,
   validates, registers.
4. The installed skill shows up in Settings → Skills, toggleable
   on/off.

No signing / review process — installed skills are opt-in. See
[docs/privacy.md](privacy.md) for how skills are sandboxed from the
app's secret store.

## 5. PR checklist

- [ ] SKILL.md follows the authoring rules above (triggers, flow,
      anti-goals, follow-ups).
- [ ] Distinct from every existing bundled skill (check
      [skills.md](skills.md)).
- [ ] Body ≤ ~300 tokens.
- [ ] If bundled: row added to the bundled-skills table.
- [ ] If installable: a README or hosting note in the PR body so
      users can find the install URL.
- [ ] Smoke-tested on a real device.

## Examples to crib from

- **`arrival-home`** — short welcome (~3 sentences), explicit
  anti-goals.
- **`sick-day`** — reversible environment overrides, explicit "no
  medical advice" refusal line.
- **`where-did-i-put-it`** — wraps existing memory tools in a
  conversational flow so users don't have to remember tool names.
- **`cooking-session`** — multi-timer choreography with broadcast
  handoffs at the end.

## Related

- [skills.md](skills.md) — format reference + bundled catalogue.
- [tools.md](tools.md) — every tool your SKILL.md can reference.
- [privacy.md](privacy.md) — what skills have access to.
